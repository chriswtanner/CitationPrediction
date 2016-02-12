import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.*;

public class LDA {

	// LDA variables
	double ldaAlpha = 100;
	double ldaGamma = 0.1;
	int optimizeInterval = 100;
	int numIterations = 1500; // 3,000
	//double totalFlutter = 0.05;
    double padding = 0.00001;
	int numTopics = 50; // 50
	String malletInput = "";
	String malletStopwords = "";
	
	// LDA-learned variables
	Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
	Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
	
	Map<String, Integer> wordToID = new HashMap<String, Integer>();
	Map<Integer, String> IDToWord = new HashMap<Integer, String>();
	
	public LDA(String malletInputFile, String stopwords) {
		this.malletInput = malletInputFile;
		this.malletStopwords = stopwords;
	}
	
	/** This class illustrates how to build a simple file filter */
    class TxtFilter implements FileFilter {
        public boolean accept(File file) {
            return file.toString().endsWith(".txt");
        }
    }
    
    Pipe pipe;
    
    public Pipe buildPipe() {
        ArrayList pipeList = new ArrayList();

        // Read data from File objects
        pipeList.add(new Input2CharSequence("UTF-8"));

        // Regular expression for what constitutes a token.
        //  This pattern includes Unicode letters, Unicode numbers, 
        //   and the underscore character. Alternatives:
        //    "\\S+"   (anything not whitespace)
        //    "\\w+"    ( A-Z, a-z, 0-9, _ )
        //    "[\\p{L}\\p{N}_]+|[\\p{P}]+"   (a group of only letters and numbers OR
        //                                    a group of only punctuation marks)
        Pattern tokenPattern = Pattern.compile("\\S+");
            //Pattern.compile("[\\p{L}\\p{N}_]+");
        		
        // Tokenize raw strings
        pipeList.add(new CharSequence2TokenSequence(tokenPattern));

        // Normalize all tokens to all lowercase
        pipeList.add(new TokenSequenceLowercase());

        // Remove stopwords from a standard English stoplist.
        //  options: [case sensitive] [mark deletions]         
        pipeList.add(new TokenSequenceRemoveStopwords(new File(malletStopwords), "UTF-8", false, false, false));

        // Rather than storing tokens as strings, convert 
        //  them to integers by looking them up in an alphabet.
        pipeList.add(new TokenSequence2FeatureSequence());

        // Do the same thing for the "target" field: 
        //  convert a class label string to a Label object,
        //  which has an index in a Label alphabet.
        pipeList.add(new Target2Label());

        // Now convert the sequence of features to a sparse vector,
        //  mapping feature IDs to counts.
        pipeList.add(new FeatureSequence2FeatureVector());

        // Print out the features and the label
        pipeList.add(new PrintInputAndTarget());

        return new SerialPipes(pipeList);
    }

    public InstanceList readDirectory(File directory) {
        return readDirectories(new File[] {directory});
    }

    public InstanceList readDirectories(File[] directories) {
        
        FileIterator iterator = new FileIterator(directories,new TxtFilter(),FileIterator.LAST_DIRECTORY);
        InstanceList instances = new InstanceList(pipe);
        instances.addThruPipe(iterator);
        return instances;
    }

    // runs LDA via mallet
	public void runLDA() throws IOException {
		System.out.println(".runLDA()");
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

        // Pipes: lowercase, tokenize, remove stopwords, map to features
        pipeList.add( new CharSequenceLowercase());
        pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\S+"))); //Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File(malletStopwords), "UTF-8", false, false, false) );
        pipeList.add( new TokenSequence2FeatureSequence() );

        InstanceList instances = new InstanceList(new SerialPipes(pipeList));
        Reader fileReader = new InputStreamReader(new FileInputStream(new File(malletInput)), "UTF-8");
        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"),3, 2, 1)); // data, label, name fields
        ParallelTopicModel model = new ParallelTopicModel(numTopics, ldaAlpha, ldaGamma);
        model.setOptimizeInterval(this.optimizeInterval);
        model.addInstances(instances);
        model.setNumThreads(2);
        model.setNumIterations(this.numIterations);
        model.estimate();
        System.out.println("alphas");
        for (int i=0; i<model.alpha.length; i++) {
        	System.out.println(model.alpha[i]);
        }
        Alphabet dataAlphabet = instances.getDataAlphabet();
        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
        
        // gets a unique wordID for every word
        int wordID = 0;
        for (int topic = 0; topic < numTopics; topic++) {
        	Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
        	while (iterator.hasNext()) {
                IDSorter idCountPair = iterator.next();
                
                String curWord = dataAlphabet.lookupObject(idCountPair.getID()).toString();
                // sets wordToID and IDToWord maps
                if (!this.wordToID.containsKey(curWord)) {
                	this.wordToID.put(curWord, wordID);
                	this.IDToWord.put(wordID, curWord);
                	wordID++;
                }
        	}
        }
        
        System.out.println(this.wordToID);
        // sets P(W|Z) for every topic
        for (int topic = 0; topic < numTopics; topic++) {
            Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
            Map<String, Double> wordCountsTotal = new HashMap<String, Double>();
            Map<String, Double> wordCountsProb = new HashMap<String, Double>();
            double total = 0;
            while (iterator.hasNext()) {
                IDSorter idCountPair = iterator.next();
                String curWord = dataAlphabet.lookupObject(idCountPair.getID()).toString();
                double curWeight = idCountPair.getWeight() + padding;
                wordCountsTotal.put(curWord, curWeight);
                total += curWeight;
            }
            
            // adds the words not found within the topic
            for (String word : this.wordToID.keySet()) {
            	if (!wordCountsTotal.keySet().contains(word)) {
            		wordCountsTotal.put(word, padding);
            		total += padding;
            	}
            }
            
            // normalizes P(W|Z) weights -- iterates over ALL words, even ones not in the topic
            //System.out.println("topic:" + topic);
            for (String word : wordCountsTotal.keySet()) {
            	wordCountsProb.put(word, wordCountsTotal.get(word) / total);
            }
            this.topicToWordProbabilities.put(topic, wordCountsProb);
            
            Iterator it = sortByValueDescending(wordCountsProb).keySet().iterator();
            while (it.hasNext()) {
            	String word = (String)it.next();
            	//System.out.println(word + "=" + wordCountsProb.get(word));
            }
            
        } // end of setting P(W|Z)
        
        // stores every P(Z|D)
        for (int i=0; i<instances.size(); i++) {
        	String curFilename = (String)instances.get(i).getTarget();
        	
        	double[] topicDistribution = model.getTopicProbabilities(i);
        	Double[] tmp = new Double[topicDistribution.length];
        	System.out.println("d:" + i);
        	for (int t=0; t<this.numTopics; t++) {
        		tmp[t] = topicDistribution[t];
        		System.out.print(tmp[t] + ",");
        	}
        	System.out.println("");
        	this.docToTopicProbabilities.put(curFilename, tmp);
        }
        
        System.out.println("done with LDA!");
	}

	// saves the important features as an object to disk
	public void saveLDA(String ldaObject) throws IOException {
		
		TopicModelObject tmp = new TopicModelObject(wordToID, IDToWord, topicToWordProbabilities, docToTopicProbabilities);
		
		FileOutputStream fileOut = new FileOutputStream(ldaObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
	}
	
	@SuppressWarnings("unchecked")
	static Map sortByValueDescending(Map map) {
		List list = new LinkedList(map.entrySet());
		Collections.sort(list, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Comparable) ((Map.Entry) (o2)).getValue()).compareTo(((Map.Entry) (o1)).getValue());
			}
		});

		Map result = new LinkedHashMap();
		for (Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
}

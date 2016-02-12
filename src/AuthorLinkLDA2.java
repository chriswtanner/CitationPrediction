import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

public class AuthorLinkLDA2 {
	
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/docsLegend.txt";
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/cora-doc.txt";
	// loads stoplist
	Set<String> stopwords = new HashSet<String>();
	
	// sufficient statistics
	Map<Integer, String> IDToWord = new HashMap<Integer, String>(); // maps wordID -> original string
	Map<String, Integer> wordToID = new HashMap<String, Integer>(); // maps original string -> word ID
	Map<Integer, String> IDToLink = new HashMap<Integer, String>(); // maps linkID -> original doc
	Map<String, Integer> linkToID = new HashMap<String, Integer>(); // maps original doc -> linkID
	Map<Integer, String> IDToAuthor = new HashMap<Integer, String>(); // maps authorID -> original doc
	Map<String, Integer> authorToID = new HashMap<String, Integer>(); // maps original author -> linkID
	
	// just for printing purposes; stores how many times each author was found	
	Map<String, Integer> authorCounts = new HashMap<String, Integer>();
	
	Map<Integer, Map<Integer, Integer>> docs = new HashMap<Integer, Map<Integer, Integer>>(); // stores doc word counts
	Map<Integer, Set<Integer>> trainingReportToSources = new HashMap<Integer, Set<Integer>>();
	
	Map<String, Integer> docNameToID = new HashMap<String, Integer>();
	Map<Integer, String> docIDToName = new HashMap<Integer, String>();
	
	Map<String, List<String>> docToAuthors = new HashMap<String, List<String>>();
	
	// tmp
	Map<String, List<String>> aToDocs = new HashMap<String, List<String>>();
	
	// model variables
	double[][] prob_word_given_topic;
	double[][] prob_link_given_topic;
	double[][] prob_topic_given_author;
	//double[][] n_dt;
	double[][] n_atWords;
	double[][] n_atLinks;
	
	double[][] n_tw = null;
	double[][] n_tl = null;	
	/*
	Set<String> reportNames = new HashSet<String>();
	Set<String> sourceNames = new HashSet<String>();
	Set<String> corpus = new HashSet<String>();
	*/
	boolean firstAuthor;
	int numTopics = 50;
	int numDocs;
	int numUniqueAuthors;
	int numUniqueWords;
	int numUniqueLinks = 1; // bogus initializing for dividing purposes
	int numIterations = 250;
	double plsaAlpha = 0.2;
    double topicPadding = 0.5; //.5;
    double wordPadding = 0.05; //0.1; // TODO: this gave the best; try 0.1
    double linkPadding = 0.005;
	public AuthorLinkLDA2(double alpha, int numIterations2, boolean fa, String malletInputFile, String trainingFile, String s, String metaFile) throws IOException {
		
		// represents the user passed in alpha and # iterations
		if (alpha != 0) {
			this.plsaAlpha = alpha;
			this.numIterations = numIterations2;
		}

		this.firstAuthor = fa;
		this.stopwords = loadList(s);

		Set<String> docNames = new HashSet<String>();
		//loadReferential(referentialFile);
		BufferedReader bin = new BufferedReader(new FileReader(malletInputFile));
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();
			docNames.add(docName);
		}
		System.out.println("# of docs: " + docNames.size());
		bin.close();
		
		
		loadMetaDocs(metaFile, docNames);
		

		
		// reads the mallet-input file, which is the same that LDA reads... stopwords I THINK are removed apriori, but
		// we check just in case
		bin = new BufferedReader(new FileReader(malletInputFile));

		int docID = 0;
		curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();
			
			st.nextToken(); // pointless token

			Map<Integer, Integer> docWordCount = new HashMap<Integer, Integer>();

			while (st.hasMoreTokens()) {
				String curWord = st.nextToken();
				if (stopwords.contains(curWord)) {
					continue;
				}
				int wordID = 0;

				// gets a unique word ID for it
				if (wordToID.containsKey(curWord)) {
					wordID = wordToID.get(curWord);
				} else {
					wordID = wordToID.keySet().size();
					wordToID.put(curWord, wordID);
					IDToWord.put(wordID, curWord);
				}

				// updates our doc's word count
				if (docWordCount.containsKey(wordID)) {
					docWordCount.put(wordID, docWordCount.get(wordID)+1);
				} else {
					docWordCount.put(wordID, 1);
				}
			}

			// forces docs to have at least 50 unique words
			if (docWordCount.keySet().size() > 0) {
				docs.put(docID, docWordCount);
				docNameToID.put(docName, docID);
				docIDToName.put(docID, docName);
				docID++;
			}
		}
		
		// creates linkIDs

			
		int linkID = 0;
		bin = new BufferedReader(new FileReader(trainingFile));
		curLine = "";
		
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String source = st.nextToken(); // it's the 1st token because .training file is source report (due to PMTLM's requirements)
			String report = st.nextToken();
			// gets a unique link ID for it
			if (linkToID.containsKey(source)) {
				linkID = linkToID.get(source);
			} else {
				linkID = linkToID.keySet().size();
				linkToID.put(source, linkID);
				IDToLink.put(linkID, source);
			}
			
			// updates report->source (training)
			Set<Integer> links = new HashSet<Integer>();
			int reportID = docNameToID.get(report);
			if (trainingReportToSources.containsKey(reportID)) {
				links = trainingReportToSources.get(reportID);
			}
			links.add(linkID);
			trainingReportToSources.put(reportID, links);
			
			System.out.println(report + " has " + this.docToAuthors.get(report).size() + " authors");
			for (String author : this.docToAuthors.get(report)) {
				
				// tmp
				List<String> d = new ArrayList<String>();
				if (aToDocs.containsKey(author)) {
					d = aToDocs.get(author);
				}
				
				if (!d.contains(report)) {
					d.add(report);
				}
				aToDocs.put(author, d);
			}
		}
		
		System.out.println("# unique links: " + IDToLink.keySet().size());
		for (Integer reportID : this.docIDToName.keySet()) {
			String report = this.docIDToName.get(reportID);
			
			// looks up 1st author:
			if (!this.docToAuthors.containsKey(report)) {
				System.out.println("missing docToMeta for " + report);
			} else {
				
				if (firstAuthor) {
					String author = this.docToAuthors.get(report).get(0);
					
					if (!authorToID.containsKey(author)) {
						int authorID = authorToID.keySet().size();
						authorToID.put(author, authorID);
						IDToAuthor.put(authorID, author);
						
						authorCounts.put(author, 1);
					} else {
						authorCounts.put(author, authorCounts.get(author) + 1);
					}
				} else {
					for (String author : this.docToAuthors.get(report)) {
						
						/*
						// tmp
						List<String> d = new ArrayList<String>();
						if (aToNumReports.containsKey(author)) {
							aToNumReports.put(author, aToNumReports.get(author)+1);
							d = aToDocs.get(author);
							
						} else {
							aToNumReports.put(author, 1);
						}
						
						d.add(report);
						aToDocs.put(author, d);
						*/
						if (!authorToID.containsKey(author)) {
							int authorID = authorToID.keySet().size();
							authorToID.put(author, authorID);
							IDToAuthor.put(authorID, author);
							
							authorCounts.put(author, 1);
						} else {
							authorCounts.put(author, authorCounts.get(author)+1);
						}
					}
				}
			}
		}
		this.numUniqueAuthors = authorToID.keySet().size();
		numDocs = docs.keySet().size();
		numUniqueWords = wordToID.keySet().size();
		numUniqueLinks = linkToID.keySet().size();
		
		prob_link_given_topic = new double[this.numTopics][numUniqueLinks];
		prob_word_given_topic = new double[this.numTopics][numUniqueWords];
		prob_topic_given_author = new double[this.numUniqueAuthors][this.numTopics];

		System.out.println("numDocs: " + numDocs);
		System.out.println("num unique authors: " + numUniqueAuthors);
		System.out.println("num unique Links: " + numUniqueLinks);
		System.out.println("num unique Words: " + numUniqueWords);
		System.out.println("# unique reports: " + trainingReportToSources.keySet().size());
	}

	
	// loads a list
	public static Set<String> loadList(String listFile) throws IOException {
		Set<String> ret = new HashSet<String>();
		if (listFile != null) {
			BufferedReader bin = new BufferedReader(new FileReader(listFile));
			String curLine = "";
			while ((curLine = bin.readLine()) != null) {
				ret.add(curLine);
			}
		}
		return ret;
	}
	
	static Map sortByValue(Map map) {
	     List list = new LinkedList(map.entrySet());
	     Collections.sort(list, new Comparator() {
	          public int compare(Object o1, Object o2) {
	               return ((Comparable) ((Map.Entry) (o2)).getValue())
	              .compareTo(((Map.Entry) (o1)).getValue());
	          }
	     });

	    Map result = new LinkedHashMap();
	    for (Iterator it = list.iterator(); it.hasNext();) {
	        Map.Entry entry = (Map.Entry)it.next();
	        result.put(entry.getKey(), entry.getValue());
	    }
	    return result;
	}

	// runs linkLDA (well, linkPLSA)
	public void runLinkLDA() {

		// initializes topic vars uniformly
		Random rand = new Random();
		double initWordGivenTopicProb = 1.0 / numUniqueWords;
		double initLinkGivenTopicProb = 1.0 / numUniqueLinks;
		double initTopicGivenAuthorProb = 1.0 / this.numTopics;

		// fills in the initial vals for prob_word_given_topic
		for (int t=0; t<this.numTopics; t++) {
			for (int w=0; w<numUniqueWords; w++) {
				prob_word_given_topic[t][w] = initWordGivenTopicProb;
			}
		}
		
		// fills in the initial vals for prob_word_given_topic
		for (int t=0; t<this.numTopics; t++) {
			for (int l=0; l<numUniqueLinks; l++) {
				prob_link_given_topic[t][l] = initLinkGivenTopicProb;
			}
		}

		// fills in the initial vals for prob_topic_given_author
		for (int a=0; a<numUniqueAuthors; a++) {
			double totalTopicProb = 0;
			for (int t=0; t<this.numTopics; t++) {
				double tmp = initTopicGivenAuthorProb * (1 + rand.nextDouble()*0.05);
				prob_topic_given_author[a][t] = tmp; 
				totalTopicProb += tmp;
			}

			// goes through again to normalize
			for (int t=0; t<this.numTopics; t++) {
				prob_topic_given_author[a][t] /= totalTopicProb;
			}
		}

		// performs EM
		for (int i=0; i<this.numIterations; i++) {
			// performs the E-step
			//********************
			// iterates over all docs and words therein
			//n_dt = new double[numDocs][this.numTopics];
			n_atWords = new double[this.numUniqueAuthors][this.numTopics];
			n_atLinks = new double[this.numUniqueAuthors][this.numTopics];
			
			n_tw = new double[this.numTopics][this.numUniqueWords];
			n_tl = new double[this.numTopics][this.numUniqueLinks];
			
			for (int d=0; d<numDocs; d++) {
				String docName = this.docIDToName.get(d);
				//System.out.println(docName + " has " + this.docToAuthors.get(this.docIDToName.get(d)));
				int aNum = 0;
				for (String author : this.docToAuthors.get(this.docIDToName.get(d))) {
					
					if (firstAuthor && aNum > 0) {
						break;
					}

					int a = this.authorToID.get(author);
					
					// adds all word tokens
					Map<Integer, Integer> docCount = docs.get(d);
					for (Integer wordID : docCount.keySet()) {
						double p = 0;
						double[] q = new double[this.numTopics];
						for (int t=0; t<this.numTopics; t++) {
							q[t] = docCount.get(wordID) * prob_topic_given_author[a][t] * prob_word_given_topic[t][wordID];
							p += q[t];
						}

						// normalizes and accumulates our counts
						for (int t=0; t<this.numTopics; t++) {
							q[t] /= p;

							n_atWords[a][t] += q[t]; //((this.plsaAlpha)*q[t]);
							n_tw[t][wordID] += q[t];
						}
					}
					
					// adds all link tokens
					if (this.trainingReportToSources.containsKey(d)) {
						for (Integer linkID : this.trainingReportToSources.get(d)) {
							double p = 0;
							double[] q = new double[this.numTopics];
							for (int t=0; t<this.numTopics; t++) {
								q[t] = prob_topic_given_author[a][t] * prob_link_given_topic[t][linkID];
								p += q[t];
							}
		
							// normalizes and accumulates our counts
							for (int t=0; t<this.numTopics; t++) {
								q[t] /= p;
		
								n_atLinks[a][t] += q[t]; //((1-this.plsaAlpha)*q[t]);
								n_tl[t][linkID] += q[t];
							}
						}
					}
					aNum++;
				} // end of looping through all authors of the current doc

			}

			// performs the M-step
			//********************
			// updates P(Z|D)
			for (int a=0; a<numUniqueAuthors; a++) {
			//for (int d=0; d<numDocs; d++) {
				
				/*
				double sum = 0;
				for (int t=0; t<this.numTopics; t++) {
					sum += (n_dt[d][t] + topicPadding);
				}

				// goes back through to normalize
				for (int t=0; t<this.numTopics; t++) {
					prob_topic_given_doc[d][t] = (n_dt[d][t] + topicPadding) / sum;
				}
				*/
				double sumWords = 0;
				for (int t=0; t<this.numTopics; t++) {
					sumWords += (n_atWords[a][t] + topicPadding);
				}

				double sumLinks = 0;
				for (int t=0; t<this.numTopics; t++) {
					if (t==0) {
						//System.out.println("ndtlinks: " + n_dtLinks[d][t]);
					}
					sumLinks += (n_atLinks[a][t] + topicPadding);
				}
				
				// goes back through to normalize
				for (int t=0; t<this.numTopics; t++) {
					prob_topic_given_author[a][t] = this.plsaAlpha*((n_atWords[a][t] + topicPadding) / sumWords) + (1-this.plsaAlpha)*((n_atLinks[a][t] + topicPadding) / sumLinks);
				}
			}

			// updates P(W|Z)
			for (int t=0; t<this.numTopics; t++) {
				double sum = 0;
				for (int w=0; w<numUniqueWords; w++) {
					sum += (n_tw[t][w] + wordPadding);
				}

				// goes back through to normalize
				for (int w=0; w<numUniqueWords; w++) {
					prob_word_given_topic[t][w] = (n_tw[t][w] + wordPadding) / sum;
				}
			}

			// updates P(L|Z)
			for (int t=0; t<this.numTopics; t++) {
				double sum = 0;
				for (int l=0; l<numUniqueLinks; l++) {
					sum += (n_tl[t][l] + linkPadding);
				}

				// goes back through to normalize
				for (int l=0; l<numUniqueLinks; l++) {
					prob_link_given_topic[t][l] = (n_tl[t][l] + linkPadding) / sum;
				}
			}
			
			// computes L (log likelihood)
			double currentL = 0;
			for (int d=0; d<numDocs; d++) {
				Map<Integer, Integer> docCount = docs.get(d);
				
				int aNum = 0;
				for (String author : this.docToAuthors.get(this.docIDToName.get(d))) {
					
					if (firstAuthor && aNum > 0) {
						break;
					}
					
					int a = this.authorToID.get(author);
				
					for (Integer wordID : docCount.keySet()) {
						double sum = 0;
						for (int t=0; t<this.numTopics; t++) {
							sum += prob_word_given_topic[t][wordID] * prob_topic_given_author[a][t];
						}
						currentL += (docCount.get(wordID)* Math.log(sum));
					}
					if (this.trainingReportToSources.containsKey(d)) {
						for (Integer linkID : this.trainingReportToSources.get(d)) {
							double sum = 0;
							for (int t=0; t<this.numTopics; t++) {
								sum += prob_link_given_topic[t][linkID] * prob_topic_given_author[a][t];
							}
							currentL += Math.log(sum);
						}
					}
					
					aNum++;
				}
			}
			System.out.println("linkLDA (" + i + ") L: " + currentL);
		} // end of EM	
	}
	
	public void printTopics() {
		// prints P(t|d)
		int authorNum = 0; //docNameToID.get("P05-1022");
		System.out.println("authorNum: " + authorNum);
		Map<Integer, Integer> doc = docs.get(authorNum);
		Map<Integer, Double> topics = new HashMap<Integer, Double>();
		for (int t=0; t<this.numTopics; t++) {
			topics.put(t, prob_topic_given_author[authorNum][t]);
		}
		Iterator it = sortByValue(topics).keySet().iterator();
		while (it.hasNext()) {
			Integer topicNum = (Integer)it.next();
			System.out.println("topic " + topicNum + " = " + topics.get(topicNum));
		}
		
		// prints P(w|t)
		System.out.println("\ntopic #\tP(W|T)\n----------------------");
		for (int t=0; t<this.numTopics; t++) {
			System.out.print("topic " + t + ": ");
			Map<Integer, Double> wordScores = new HashMap<Integer, Double>();
			
			double total = 0;
			for (int w=0; w<numUniqueWords; w++) {
				wordScores.put(w, n_tw[t][w]); // (n_tw[t][w] + 5)/(total + 5*this.numUniqueWords)); //
			}
			
			it = sortByValue(wordScores).keySet().iterator();
			for (int i=0; i<15 && it.hasNext(); i++) {
				Integer wordID = (Integer)it.next();
				System.out.print(IDToWord.get(wordID) + " "); //= " + wordScores.get(wordID));
			}
			System.out.println("");
		}
	}
	

	public void saveLinkLDA(String lObject) throws IOException {
		
		Map<Integer, Map<String, Double>> topicToLinkProbabilities = new HashMap<Integer, Map<String, Double>>();
		for (int t=0; t<this.numTopics; t++) {
			Map<String, Double> linkProbs = new HashMap<String, Double>();
			for (int l=0; l<numUniqueLinks; l++) {
				linkProbs.put(this.IDToLink.get(l), prob_link_given_topic[t][l]);
				if (this.IDToLink.get(l).equals("W06-1601")) {
					System.out.println("**** WE ARE ADDING THE SOURCE W06-1601 "+ t);
				}
			}
			topicToLinkProbabilities.put(t, linkProbs);
		}
		
		Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
		for (int t=0; t<this.numTopics; t++) {
			Map<String, Double> wordProbs = new HashMap<String, Double>();
			for (int w=0; w<numUniqueWords; w++) {
				wordProbs.put(this.IDToWord.get(w), prob_word_given_topic[t][w]);
			}
			topicToWordProbabilities.put(t, wordProbs);
		}
	
		Map<String, Double[]> authorToTopicProbabilities = new HashMap<String, Double[]>();
		for (int a=0; a<numUniqueAuthors; a++) {
		//for (int d=0; d<numDocs; d++) {
			Double[] topicProbs = new Double[this.numTopics];
			for (int t=0; t<this.numTopics; t++) {
				topicProbs[t] = prob_topic_given_author[a][t];
			}
			authorToTopicProbabilities.put(this.IDToAuthor.get(a), topicProbs);
		}
		
		AuthorLinkLDA2ModelObject tmp = new AuthorLinkLDA2ModelObject(linkToID, IDToLink, topicToLinkProbabilities, topicToWordProbabilities, authorToTopicProbabilities);
		
		FileOutputStream fileOut = new FileOutputStream(lObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
	}
	
	private void loadMetaDocs(String metaFile, Set<String> docNames) throws IOException {
		
		System.out.println("loading metadocs' authors");
		if (!metaFile.equals("")) {
			// loads the MetaDocs
			docToAuthors = new HashMap<String, List<String>>();
			
			BufferedReader bin = new BufferedReader(new FileReader(metaFile));
			String curLine = "";
			while ((curLine = bin.readLine())!=null) {
				
				// expects an 'id'
				if (!curLine.contains("id")) {
					continue;
				}
				
				// id line
				StringTokenizer st = new StringTokenizer(curLine);
				st.nextToken();
				st.nextToken();
				String docID = st.nextToken();
				docID = docID.replace("{", "").replace("}", "");
				
				if (!docNames.contains(docID)) {
					continue;
				}
				String authorLine = bin.readLine().toLowerCase();
				authorLine = authorLine.substring(authorLine.indexOf("{")+1).replace("}", "");
				st = new StringTokenizer(authorLine, ";");
				
				List<String> authorTokens = new ArrayList<String>();
				while (st.hasMoreTokens()) {
					String authorToken = st.nextToken().trim();
					if (authorToken.length() > 1) {
						
						boolean containsDigit = false;
						for (int j=0; j<authorToken.length(); j++) {
							if (Character.isDigit(authorToken.charAt(j))) {
								containsDigit = true;
								break;
							}
						}
						
						if (!containsDigit) {
							authorTokens.add(authorToken);
						}
					}
				}
				
				// skip over venue
				String title = bin.readLine();
				title = title.substring(title.indexOf("{")+1).replace("}", "");
		
				bin.readLine();
				
				// year line
				curLine = bin.readLine();
				st = new StringTokenizer(curLine);
				st.nextToken();
				st.nextToken();
				String year = st.nextToken();
				year = year.replace("{", "").replace("}", "");
				
				// sometimes the file incorrectly puts } on the next line
				MetaDoc md = new MetaDoc(docID, title, year, authorTokens);
				this.docToAuthors.put(docID, authorTokens);
				//System.out.println("adding meta");
			}
			//System.out.println("meta doc size: " + docToMeta.keySet().size());
		}
		System.out.println("DONE w/ metadocs' authors");
	}


	public void printStats(String statsFile) throws IOException {
		BufferedWriter bout = new BufferedWriter(new FileWriter(statsFile));
		
		bout.write("# authors: " + this.authorToID.keySet().size() + "\n");
		Map<String, Integer> aToNumReports = new HashMap<String, Integer>();
		for (String a : aToDocs.keySet()) {
			aToNumReports.put(a,  aToDocs.get(a).size());
		}
		
		Iterator it = sortByValueDescending(aToNumReports).keySet().iterator();
		while (it.hasNext()) {
			String a = (String)it.next();
			double totaltotalJ = 0;
			bout.write(a + "\t" + aToNumReports.get(a) + "\n");
			for (int i=0; i<this.aToDocs.get(a).size(); i++) {
				String docA = aToDocs.get(a).get(i);
				bout.write(docA);
				Set<Integer> linksA = this.trainingReportToSources.get(this.docNameToID.get(docA));
				
				// calculate jaccard here
				double totalJ = 0;
				for (int j=0; j<this.aToDocs.get(a).size(); j++) {
					if (i == j) { continue; }
					String docB = aToDocs.get(a).get(j);
					Set<Integer> linksB = this.trainingReportToSources.get(this.docNameToID.get(docB));
					int intersection = 0;
					for (Integer l : linksB) {
						if (linksA.contains(l)) {
							intersection++;
						}
					}
					//bout.write("int: " + intersection + "; A: " + linksA.size() + "; B: " + linksB.size() + "\n");
					double curJ = (double)intersection; // / (double)(linksA.size() - linksB.size() - intersection);
					totalJ += curJ;
				}
				totalJ /= (double)(this.aToDocs.get(a).size()-1);
				totaltotalJ += totalJ;
				bout.write(docA + " " + totalJ + " ");
				List<Integer> list = new ArrayList(linksA);
				Collections.sort(list);
				
				for (Integer ix : list) {
					bout.write(ix.intValue() + " ");
				}
				bout.write("\n");
			}
			totaltotalJ /= ((double)this.aToDocs.get(a).size());
			bout.write("\tavgavgJaccard:" + totaltotalJ + "\n");

			

		}
		bout.close();
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

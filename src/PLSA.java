import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
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

public class PLSA {
	
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/docsLegend.txt";
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/cora-doc.txt";
	String googV = "/Users/christanner/research/projects/CitationFinder/eval/google_vocab.txt";
	// loads stoplist
	Set<String> stopwords = new HashSet<String>();

	// sufficient statistics
	Map<Integer, String> IDToWord = new HashMap<Integer, String>(); // maps wordID -> original string
	Map<String, Integer> wordToID = new HashMap<String, Integer>(); // maps original string -> word ID
	Map<Integer, String> IDToLink = new HashMap<Integer, String>(); // maps linkID -> original doc
	Map<String, Integer> linkToID = new HashMap<String, Integer>(); // maps original doc -> linkID
	
	Map<Integer, Map<Integer, Integer>> docs = new HashMap<Integer, Map<Integer, Integer>>(); // stores doc word counts
	Map<Integer, Set<Integer>> trainingReportToSources = new HashMap<Integer, Set<Integer>>();
	
	Map<String, Integer> docNameToID = new HashMap<String, Integer>();
	Map<Integer, String> docIDToName = new HashMap<Integer, String>();
	
	// model variables
	double[][] prob_word_given_topic;
	double[][] prob_link_given_topic;
	double[][] prob_topic_given_doc;
	double[][] n_dt;
	double[][] n_dtWords;
	double[][] n_dtLinks;
	
	double[][] n_tw = null;
	double[][] n_tl = null;	
	
	// the below 3 lines are just for briefly experimenting with seeing if P(Z|R)P(Z|L) works, where a Link doesn't have to be in our corpus
	//public static String testingOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_5000.testing";
	public static Map<String, Set<String>> allValidReportToSources = new HashMap<String, Set<String>>(); // all doc -> docs which have metadata for all docs
	public static Map<String, Set<String>> reportToTestingSources = new HashMap<String, Set<String>>();
	
	/*
	Set<String> reportNames = new HashSet<String>();
	Set<String> sourceNames = new HashSet<String>();
	Set<String> corpus = new HashSet<String>();
	*/
	
	int numTopics = 50;
	int numDocs;
	int numUniqueWords;
	int numUniqueLinks = 1; // bogus initializing for dividing purposes
	int numIterations = 1000;
	double plsaAlpha = .99;
    double topicPadding = .5; //.5;
    double wordPadding = 0.05; //0.1; // TODO: this gave the best; try 0.1
    double linkPadding = 0.005;
    public static String aclNetworkFile = "";
	public PLSA(String dataDir, double alpha, int numIterations2, String malletInputFile, String trainingFile, String s, boolean linkLDAModel) throws IOException {
		
    	aclNetworkFile = dataDir + "acl.txt";
		// represents the user passed in alpha and # iterations
		if (alpha != 0) {
			this.plsaAlpha = alpha;
			this.numIterations = numIterations2;
		}
		
		this.stopwords = loadList(s);
		//loadTesting(this.testingOutFile);
		loadReferential(aclNetworkFile);
		/*
		// constructs the report names and source names from docsLegend
		BufferedReader bin = new BufferedReader(new FileReader(docsLegend));
		String curLine = "";
		bin.readLine();
		bin.readLine();
		bin.readLine();
		while ((curLine = bin.readLine())!=null) {

			String[] tokens = curLine.split("\t");
			String doc = tokens[0];
			boolean isReport = true;
			boolean isSource = true;
			if (tokens.length == 2) {
				isSource = false;
			} else { // have all 3 tokens
				if (!tokens[1].equals("*")) {
					isReport = false;
				}
			}

			corpus.add(doc);
			if (isReport) {
				reportNames.add(doc);
			}
			if (isSource) {
				sourceNames.add(doc);
			}
		}
		*/
		// reads the mallet-input file, which is the same that LDA reads... stopwords I THINK are removed apriori, but
		// we check just in case
		BufferedReader bin = new BufferedReader(new FileReader(malletInputFile));
		Set<String> malletVocab = new HashSet<String>();
		Set<String> googVocab = new HashSet<String>();
		
		int docID = 0;
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();

			// only process docs that are actually reports or sources
			// skips over docs that may be repeated twice in mallet-input, although i don't think there are duplicates
			/*
			if (docNameToID.containsKey(docName) || (!reportNames.contains(docName)) && !sourceNames.contains(docName)) {
				System.out.println("mallet had " + docName + " but we don't have it in the docsLegend");
				continue;
			}
			*/
			
			st.nextToken(); // pointless token

			Map<Integer, Integer> docWordCount = new HashMap<Integer, Integer>();

			while (st.hasMoreTokens()) {
				String curWord = st.nextToken();
				if (stopwords.contains(curWord)) {
					continue;
				}
				
				// tmp
				malletVocab.add(curWord);
				
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
		
		// optionally creates linkIDs
		if (linkLDAModel) {
			
			int linkID = 0;
			bin = new BufferedReader(new FileReader(trainingFile));
			curLine = "";
			System.out.println("we in here");
			/*
			for (String report : this.allValidReportToSources.keySet()) {
				
				if (!docNameToID.containsKey(report)) {
					continue;
				}
				
				for (String source : this.allValidReportToSources.get(report)) {
				*/
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
			}
				

			//} // remove this one once i'm done experimenting
			// prints # of links per doc
			for (int did : this.docIDToName.keySet()) {
				if (trainingReportToSources.containsKey(did)) {
					System.out.println("doc " + this.docIDToName.get(did) + " contains " + trainingReportToSources.get(did).size() + " links");
				} else {
					System.out.println("** doc " + this.docIDToName.get(did) + " contains 0 links");
				}
			}
			System.out.println("# unique links: " + IDToLink.keySet().size());
		}
		
		bin = new BufferedReader(new FileReader(googV));
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String w = st.nextToken();
			
			googVocab.add(w);
			googVocab.add(w.toLowerCase());
		}
		
		Set<String> OOV = new HashSet<String>();
		System.out.println("****** IN VOCAB:");
		for (String w : malletVocab) {
			if (!googVocab.contains(w)) {
				OOV.add(w);
			} else {
				System.out.println(w);
			}
		}
		
		System.out.println("***** OUT OF VOCAB:");
		for (String w : OOV) {
			System.out.println(w);
		}
		System.out.println("\n\ngoogle didn't have " + OOV.size() + " of " + malletVocab.size() + " (" + (double)OOV.size() / (double)malletVocab.size() + " words");
		System.exit(1);
		numDocs = docs.keySet().size();
		numUniqueWords = wordToID.keySet().size();
		numUniqueLinks = linkToID.keySet().size();
		
		prob_link_given_topic = new double[this.numTopics][numUniqueLinks];
		prob_word_given_topic = new double[this.numTopics][numUniqueWords];
		prob_topic_given_doc = new double[numDocs][this.numTopics];

		System.out.println("numDocs: " + numDocs);
		System.out.println("num unique Links: " + numUniqueLinks);
		System.out.println("num unique Words: " + numUniqueWords);
		System.out.println("# unique reports: " + trainingReportToSources.keySet().size());
		// used to print any missing docs
		/*
		// just prints if any docs are missing for the corresponding mallet-input
		for (String doc : docNameToID.keySet()) {
			if (!corpus.contains(doc)) {
				System.out.println("corpus didn't have : " + doc);
			}
		}

		// just prints if mallet-input is missing any of reports or sources
		for (String report : reportNames) {
			if (!docNameToID.containsKey(report)) {
				System.out.print("report: " + report + " not found");
			}
		}
		for (String source : sourceNames) {
			if (!docNameToID.containsKey(source)) {
				System.out.println("source: " + source + " not found");					
			}
		}
		*/
	}

	/*
	private void loadTesting(String t) throws IOException {
		// TODO Auto-generated method stub
		BufferedReader bin = new BufferedReader(new FileReader(t));
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String source = st.nextToken();
			String report = st.nextToken();
			
			Set<String> curSources = new HashSet<String>();
			if (reportToTestingSources.containsKey(report)) {
				curSources = reportToTestingSources.get(report);
			}
			curSources.add(source);
			reportToTestingSources.put(report, curSources);
		}
	}
	*/


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
	
	private static void loadReferential(String ref) throws IOException {
		BufferedReader bin = new BufferedReader(new FileReader(ref));

		allValidReportToSources = new HashMap<String, Set<String>>();
		
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			// ensures we have report ==> source
			
			if (st.countTokens() != 3) {
				continue;
			}
			
			String report = st.nextToken();
			st.nextToken(); // arrow
			String source = st.nextToken();
						
			
			// updates our map of Report -> {sources}
			Set<String> curSources = new HashSet<String>();
			if (allValidReportToSources.containsKey(report)) {
				curSources = allValidReportToSources.get(report);
			}
			if (!report.equals(source) && !(reportToTestingSources.containsKey(report) && reportToTestingSources.get(report).contains(source))) {
				
				curSources.add(source);
			}
			allValidReportToSources.put(report, curSources);
		}
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
		double initTopicGivenDocProb = 1.0 / this.numTopics;

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

		// fills in the initial vals for prob_topic_given_doc
		for (int d=0; d<numDocs; d++) {
			double totalTopicProb = 0;
			for (int t=0; t<this.numTopics; t++) {
				double tmp = initTopicGivenDocProb * (1 + rand.nextDouble()*0.05);
				prob_topic_given_doc[d][t] = tmp; 
				totalTopicProb += tmp;
			}

			// goes through again to normalize
			for (int t=0; t<this.numTopics; t++) {
				prob_topic_given_doc[d][t] /= totalTopicProb;
			}
		}

		// performs EM
		for (int i=0; i<this.numIterations; i++) {
			// performs the E-step
			//********************
			// iterates over all docs and words therein
			//n_dt = new double[numDocs][this.numTopics];
			n_dtWords = new double[numDocs][this.numTopics];
			n_dtLinks = new double[numDocs][this.numTopics];
			
			n_tw = new double[this.numTopics][this.numUniqueWords];
			n_tl = new double[this.numTopics][this.numUniqueLinks];
			
			for (int d=0; d<numDocs; d++) {

				
				// adds all word tokens
				Map<Integer, Integer> docCount = docs.get(d);
				for (Integer wordID : docCount.keySet()) {
					double p = 0;
					double[] q = new double[this.numTopics];
					for (int t=0; t<this.numTopics; t++) {
						q[t] = docCount.get(wordID) * prob_topic_given_doc[d][t] * prob_word_given_topic[t][wordID];
						p += q[t];
					}

					// normalizes and accumulates our counts
					for (int t=0; t<this.numTopics; t++) {
						q[t] /= p;

						n_dtWords[d][t] += q[t]; //((this.plsaAlpha)*q[t]);
						n_tw[t][wordID] += q[t];
					}
				}
				
				// adds all link tokens
				if (this.trainingReportToSources.containsKey(d)) {
					for (Integer linkID : this.trainingReportToSources.get(d)) {
						double p = 0;
						double[] q = new double[this.numTopics];
						for (int t=0; t<this.numTopics; t++) {
							q[t] = prob_topic_given_doc[d][t] * prob_link_given_topic[t][linkID];
							p += q[t];
						}
	
						// normalizes and accumulates our counts
						for (int t=0; t<this.numTopics; t++) {
							q[t] /= p;
	
							n_dtLinks[d][t] += q[t]; //((1-this.plsaAlpha)*q[t]);
							n_tl[t][linkID] += q[t];
						}
					}
				}
			}

			// performs the M-step
			//********************
			// updates P(Z|D)
			for (int d=0; d<numDocs; d++) {
				
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
					sumWords += (n_dtWords[d][t] + topicPadding);
				}

				double sumLinks = 0;
				for (int t=0; t<this.numTopics; t++) {
					if (t==0) {
						//System.out.println("ndtlinks: " + n_dtLinks[d][t]);
					}
					sumLinks += (n_dtLinks[d][t] + topicPadding);
				}
				
				// goes back through to normalize
				for (int t=0; t<this.numTopics; t++) {
					prob_topic_given_doc[d][t] = this.plsaAlpha*((n_dtWords[d][t] + topicPadding) / sumWords) + (1-this.plsaAlpha)*((n_dtLinks[d][t] + topicPadding) / sumLinks);
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
				for (Integer wordID : docCount.keySet()) {
					double sum = 0;
					for (int t=0; t<this.numTopics; t++) {
						sum += prob_word_given_topic[t][wordID] * prob_topic_given_doc[d][t];
					}
					currentL += (docCount.get(wordID)* Math.log(sum));
				}
				if (this.trainingReportToSources.containsKey(d)) {
					for (Integer linkID : this.trainingReportToSources.get(d)) {
						double sum = 0;
						for (int t=0; t<this.numTopics; t++) {
							sum += prob_link_given_topic[t][linkID] * prob_topic_given_doc[d][t];
						}
						currentL += Math.log(sum);
					}
				}
			}
			System.out.println("linkLDA (" + i + ") L: " + currentL);
		} // end of EM	
	}
	
	public void printTopics() {
		// prints P(t|d)
		int docNum = 0; //docNameToID.get("P05-1022");
		System.out.println("docNum: " + docNum);
		Map<Integer, Integer> doc = docs.get(docNum);
		Map<Integer, Double> topics = new HashMap<Integer, Double>();
		for (int t=0; t<this.numTopics; t++) {
			topics.put(t, prob_topic_given_doc[docNum][t]);
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
	
	// runs regular PLSA
	public void runPLSA() {

		// initializes topic vars uniformly
		Random rand = new Random();
		double initWordGivenTopicProb = 1.0 / numUniqueWords;
		double initTopicGivenDocProb = 1.0 / this.numTopics;

		// fills in the initial vals for prob_word_given_topic
		for (int t=0; t<this.numTopics; t++) {
			for (int w=0; w<numUniqueWords; w++) {
				prob_word_given_topic[t][w] = initWordGivenTopicProb;
			}
		}

		// fills in the initial vals for prob_topic_given_doc
		for (int d=0; d<numDocs; d++) {
			double totalTopicProb = 0;
			for (int t=0; t<this.numTopics; t++) {
				double tmp = initTopicGivenDocProb * (1 + rand.nextDouble()*0.05);
				prob_topic_given_doc[d][t] = tmp; 
				totalTopicProb += tmp;
			}

			// goes through again to normalize
			for (int t=0; t<this.numTopics; t++) {
				prob_topic_given_doc[d][t] /= totalTopicProb;
			}
		}

		// performs EM
		for (int i=0; i<this.numIterations; i++) {
			// performs the E-step
			//********************
			// iterates over all docs and words therein
			n_dt = new double[numDocs][this.numTopics];
			n_tw = new double[this.numTopics][numUniqueWords];

			for (int d=0; d<numDocs; d++) {
				Map<Integer, Integer> docCount = docs.get(d);
				for (Integer wordID : docCount.keySet()) {

					double p = 0;
					double[] q = new double[this.numTopics];

					for (int t=0; t<this.numTopics; t++) {
						q[t] = docCount.get(wordID) * prob_topic_given_doc[d][t] * prob_word_given_topic[t][wordID];
						p += q[t];
					}

					// normalizes and accumulates our counts
					for (int t=0; t<this.numTopics; t++) {
						q[t] /= p;

						n_dt[d][t] += q[t];
						n_tw[t][wordID] += q[t];
					}
				}
			}

			// performs the M-step
			//********************
			// updates P(Z|D)
			for (int d=0; d<numDocs; d++) {
				double sum = 0;
				for (int t=0; t<this.numTopics; t++) {
					sum += (n_dt[d][t] + topicPadding);
				}

				// goes back through to normalize
				for (int t=0; t<this.numTopics; t++) {
					prob_topic_given_doc[d][t] = (n_dt[d][t] + topicPadding) / sum;
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

			// computes L (log likelihood)
			double currentL = 0;
			for (int d=0; d<numDocs; d++) {
				Map<Integer, Integer> docCount = docs.get(d);
				for (Integer wordID : docCount.keySet()) {
					double sum = 0;
					for (int t=0; t<this.numTopics; t++) {
						sum += prob_word_given_topic[t][wordID] * prob_topic_given_doc[d][t];
					}
					currentL += (docCount.get(wordID)* Math.log(sum));
				}
			}
			System.out.println("PLSA (" + i + "); L: " + currentL);
		} // end of EM	
		
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
	
		Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
		for (int d=0; d<numDocs; d++) {
			Double[] topicProbs = new Double[this.numTopics];
			for (int t=0; t<this.numTopics; t++) {
				topicProbs[t] = prob_topic_given_doc[d][t];
			}
			docToTopicProbabilities.put(this.docIDToName.get(d), topicProbs);
		}
		
		LinkLDAModelObject tmp = new LinkLDAModelObject(linkToID, IDToLink, topicToLinkProbabilities, topicToWordProbabilities, docToTopicProbabilities);
		
		FileOutputStream fileOut = new FileOutputStream(lObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
	}
	
	public void savePLSA(String plsaObject) throws IOException {
		
		Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
		for (int t=0; t<this.numTopics; t++) {
			Map<String, Double> wordProbs = new HashMap<String, Double>();
			for (int w=0; w<numUniqueWords; w++) {
				wordProbs.put(this.IDToWord.get(w), prob_word_given_topic[t][w]);
			}
			topicToWordProbabilities.put(t, wordProbs);
		}
		
		Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
		for (int d=0; d<numDocs; d++) {
			Double[] topicProbs = new Double[this.numTopics];
			for (int t=0; t<this.numTopics; t++) {
				topicProbs[t] = prob_topic_given_doc[d][t];
			}
			
			docToTopicProbabilities.put(this.docIDToName.get(d), topicProbs);
		}
		
		TopicModelObject tmp = new TopicModelObject(wordToID, IDToWord, topicToWordProbabilities, docToTopicProbabilities);
		
		FileOutputStream fileOut = new FileOutputStream(plsaObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
	}

}

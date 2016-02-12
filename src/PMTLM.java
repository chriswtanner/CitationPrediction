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

import org.apache.commons.math3.distribution.PoissonDistribution;

public class PMTLM {

	String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/docsLegend.txt";
	
	// loads stoplist
	Set<String> stopwords = new HashSet<String>();

	// sufficient statistics
	Map<Integer, String> IDToWord = new HashMap<Integer, String>(); // maps wordID -> original string
	Map<String, Integer> wordToID = new HashMap<String, Integer>(); // maps original string -> word ID
	Map<Integer, Map<Integer, Integer>> docs = new HashMap<Integer, Map<Integer, Integer>>();
	Map<Integer, Integer> docNumWords = new HashMap<Integer, Integer>();
	Map<String, Integer> docNameToID = new HashMap<String, Integer>();
	Map<Integer, String> docIDToName = new HashMap<Integer, String>();
	
	// model variables
	double[][] prob_word_given_topic;
	double[][] prob_topic_given_doc;
	double[][][] q_d_dprime_z;
	double[][] n_dt;
	double[][] n_tw;
	double[] n_z;
	//Map<Integer, Integer[]> a_d_dprime = new HashMap<Integer, Integer[]>();
	Map<String, Integer> a = new HashMap<String, Integer>();
	
	Set<String> reportNames = new HashSet<String>();
	Set<String> sourceNames = new HashSet<String>();
	Set<String> corpus = new HashSet<String>();
	
	int numTopics = 50;
	int numDocs;
	int numUniqueWords;
	int numIterations = 200;
	double alpha = 0.3;
    double topicPadding = 0.0001; //0001;
    double wordPadding = 0.000001;
    
	public PMTLM(String malletInputFile, String s) throws IOException {

		this.stopwords = loadList(s);

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

		// reads the mallet-input file, which is the same that LDA reads... stopwords I THINK are removed apriori, but
		// we check just in case
		bin = new BufferedReader(new FileReader(malletInputFile));

		int docID = 0;
		curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();

			// only process docs that are actually reports or sources
			// skips over docs that may be repeated twice in mallet-input, although i don't think there are duplicates
			if (docNameToID.containsKey(docName) || (!reportNames.contains(docName)) && !sourceNames.contains(docName)) {
				System.out.println("mallet had " + docName + " but we don't have it in the docsLegend");
				continue;
			}

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
			if (docWordCount.keySet().size() > 50) {
				docs.put(docID, docWordCount);
				docNameToID.put(docName, docID);
				docIDToName.put(docID, docName);
				
				// gets total count
				int totalWords = 0;
				for (int wordID : docWordCount.keySet()) {
					totalWords += docWordCount.get(wordID);
				}
				docNumWords.put(docID, totalWords);
				docID++;
			}
		}
		numDocs = docs.keySet().size();
		numUniqueWords = wordToID.keySet().size();
		System.out.println("# docs = " + numDocs);
		System.out.println("# unique words = " + numUniqueWords);
		prob_word_given_topic = new double[this.numTopics][numUniqueWords];
		prob_topic_given_doc = new double[numDocs][this.numTopics];
		//h_dwz = new double[numDocs][this.numUniqueWords][this.numTopics];
		//q_d_dprime_z = new double[numDocs][numDocs][this.numTopics];
		n_z = new double[this.numTopics];
		
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


	public void runEM() {
		
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
		
		// fills in the initial vals for n_z
		for (int t=0; t<this.numTopics; t++) {
			n_z[t] = (double)this.numTopics;
		}
		
		
		
		// performs EM
		for (int i=0; i<this.numIterations; i++) {
			//a_d_dprime = new HashMap<Integer, Integer[]>();
			// performs the E-step
			//********************
			// iterates over all docs and words therein
			//h_dwz = new double[numDocs][this.numUniqueWords][this.numTopics];
			q_d_dprime_z = new double[numDocs][numDocs][this.numTopics];
			a = new HashMap<String, Integer>();
			
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
						n_tw[t][wordID] += (q[t] / (double)this.docNumWords.get(d));
					}
				}
				// estimates q_dd'(z)
				for (int d2=0; d2<numDocs; d2++) {
					if (d2 == d) {
						continue;
					}
					double sum = 0.0;
					for (int t=0; t<this.numTopics; t++) {
						q_d_dprime_z[d][d2][t] = 0.000001 + prob_topic_given_doc[d][t] * prob_topic_given_doc[d2][t] * n_z[t];
						if (Double.isNaN(q_d_dprime_z[d][d2][t])) {
							System.out.println("q_dd is nan 1st!");
							System.exit(1);
						}
						sum += q_d_dprime_z[d][d2][t];
					}
					
					for (int t=0; t<this.numTopics; t++) {
						q_d_dprime_z[d][d2][t] /= sum;
					}
				}
				/*
				Map<Integer, Integer> docCount = docs.get(d);
				for (Integer wordID : docCount.keySet()) {
				
					double sum = 0.0;
					for (int t=0; t<this.numTopics; t++) {
						h_dwz[d][wordID][t] = prob_topic_given_doc[d][t] * prob_word_given_topic[t][wordID];
						sum += h_dwz[d][wordID][t];
					}
					// normalizes
					for (int t=0; t<this.numTopics; t++) {
						h_dwz[d][wordID][t] /= sum;
					}
				}
				*/
				// sets q_dd'(z)

				
			} // end of E-step
			
			// performs the M-step
			//********************
			// updates P(Z|D) (aka theta_dz)
			for (int d=0; d<numDocs; d++) {
				
				// pre-processes it by calculating A_dd' first
				/*
				Integer[] cur_A = new Integer[this.numDocs];
				for (int d2=0; d2<numDocs; d2++) {
					if (d==d2) {
						continue;
					}
					cur_A[d2] = getPoisson(d,d2);
				}
				// stores A_dd' so that n_z doesn't have to sample every A_dd' again
				a_d_dprime.put(d, cur_A);
				*/
				
				// pre-process by calculating A first
				for (int d2=0; d2<numDocs; d2++) {
					if (d==d2) {
						continue;
					}
					int tmp = getPoisson(d,d2);
					if (tmp > 0) {
						String key = String.valueOf(d) + "_" + String.valueOf(d2);
						a.put(key, tmp);
					}
				}
				// calculates P(Z|D)'s left side
				double sum = 0;
				for (int t=0; t<this.numTopics; t++) {
					double leftSide = (this.alpha/this.docNumWords.get(d)) * n_dt[d][t];
					double kd = 0;
					double rightSide = 0;
					for (int d2=0; d2<numDocs; d2++) {
						if (d==d2) {
							continue;
						}
						String key = String.valueOf(d) + "_" + String.valueOf(d2);
						if (a.containsKey(key)) {
							rightSide += (a.get(key) * q_d_dprime_z[d][d2][t]);
							kd += a.get(key);
						}
					}
					rightSide *= (1-this.alpha);
					
					double denom = this.alpha + (1-this.alpha)*kd;
					prob_topic_given_doc[d][t] = (leftSide + rightSide) / denom;
					sum += prob_topic_given_doc[d][t];
				}
				//System.out.println("P(Z|D) across all topics: " + sum);
				/* plsa-style
				double leftSum = 0;
				for (int t=0; t<this.numTopics; t++) {
					leftSum += (n_dt[d][t] + topicPadding);
				}

				
				// calculates P(Z|D)'s right side
				double rightSum = 0;
				double[] rightSides = new double[this.numTopics];
				for (int t=0; t<this.numTopics; t++) {
					
					double rightSide = 0.0;

					int numAdd0 = 0;
					int numQdd0 = 0;
					for (int d2=0; d2<numDocs; d2++) {
						if (d2 == d) {
							continue;
						}
						if (cur_A[d2] == 0) {
							numAdd0++;
						}
						if (q_d_dprime_z[d][d2][t] == 0) {
							numQdd0++;
						}
						rightSide += (cur_A[d2] * q_d_dprime_z[d][d2][t]);
						if (Double.isNaN(rightSide)) {
							System.out.println("cur_A[d2] = " + cur_A[d2] + "; qdd[d][d2][t] = " + q_d_dprime_z[d][d2][t]);
						}
					}
					
					if (rightSide == 0) {
						System.out.println("rightside[d][t] across all d2's: " + numAdd0 + " and " + numQdd0);
					}
					
					//rightSide += 0.000001;
					rightSides[t] = rightSide;
					rightSum += rightSide;
					
					if (Double.isNaN(rightSides[t])) {
						System.out.println("rightsides of " + t + " is nan!");
					}
				}
				if (Double.isNaN(rightSum)) {
					System.out.println("rightsum is Nan!");
				}
				if (leftSum == 0) {
					System.out.println("LEFT SUM IS 0!!");
				}
				// goes back through to normalize
				for (int t=0; t<this.numTopics; t++) {
					prob_topic_given_doc[d][t] = this.alpha*((n_dt[d][t] + topicPadding) / leftSum) + (1-this.alpha)*(rightSides[t] / rightSum) + 0.000001;
					if (Double.isNaN(prob_topic_given_doc[d][t])) {
						System.out.println("setting P(Z|D) to be NaN; (n_dt[d][t] + topicPadding) = " + (n_dt[d][t] + topicPadding) + "; leftSum: " + leftSum + "; (rightSides[t] / rightSum):" + (rightSides[t] / rightSum) + "; rightSides[t] = " + rightSides[t] + "; rightSum: " + rightSum);
					}
				}
				*/
				/*
				double total = 0.0;
				for (int t=0; t<this.numTopics; t++) {
					double leftSide = (this.alpha/this.docNumWords.get(d)) * (n_dt[d][t] + topicPadding);
					
					double rightSide = 0.0;
					// calculates k_d once
					double kd = 0.0;
					for (int d2=0; d2<numDocs; d2++) {
						if (d2 == d) {
							continue;
						}
						double ad_dprime = cur_A[d2];
						rightSide += (ad_dprime * q_d_dprime_z[d][d2][t]);
						kd += ad_dprime;
					}
					rightSide *= (1-this.alpha);
					
					double denom = this.alpha + (1-this.alpha)*kd;
					prob_topic_given_doc[d][t] = (leftSide + rightSide) / denom;
					total += prob_topic_given_doc[d][t];
				}
				*/
				//System.out.println("P(Z|D) over all topics = " + total);
			} // end of M-step


			
			// pre-processes by calculating Beta_z first (which will be used in the denoms)
			/*
			double[] beta_z = new double[this.numTopics];
			for (int t=0; t<this.numTopics; t++) {
				double total = 0.0;
				for (int d=0; d<numDocs; d++) {
					
					double sumOverWords = 0.0;
					Map<Integer, Integer> docCount = docs.get(d);
					for (Integer wordID : docCount.keySet()) {
						sumOverWords += (docCount.get(wordID) * h_dwz[d][wordID][t]);
					}
					
					total += ((1.0 / (double)this.docNumWords.get(d)) * sumOverWords);
				}
				beta_z[t] = total;
			}
			
			// calculates P(W|Z) aka Beta_z_w
			for (int t=0; t<this.numTopics; t++) {
				double totalOverWords = 0.0;
				for (int w=0; w<numUniqueWords; w++) {

					double numerator = 0.0;	
					for (int d=0; d<numDocs; d++) {
						
						Map<Integer, Integer> docCount = docs.get(d);
						if (docCount.containsKey(w)) {
							numerator += ((1.0 / (double)this.docNumWords.get(d)) * (double)docCount.get(w) * (h_dwz[d][w][t]));
						}
					}
					prob_word_given_topic[t][w] = (numerator / beta_z[t]);
					totalOverWords += prob_word_given_topic[t][w];
				}
				System.out.println("P(W|Z) over all words: " + totalOverWords);
			}
			*/
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
			
			// calculates n_z
			for (int t=0; t<this.numTopics; t++) {
				double numerator = 0.0;
				for (int d=0; d<numDocs; d++) {
					for (int d2=0; d2<numDocs; d2++) {
						if (d==d2) {
							continue;
						}
						String key = String.valueOf(d) + "_" + String.valueOf(d2);
						if (a.containsKey(key)) {
							numerator += ((double)a.get(key) * q_d_dprime_z[d][d2][t]);
						}
					}
				}

				double denom = 0.0;
				for (int d=0; d<numDocs; d++) {
					denom += prob_topic_given_doc[d][t];
				}
				denom = Math.pow(denom, 2);

				n_z[t] = numerator / denom;
				if (Double.isNaN(n_z[t])) {
					System.out.println("n_z is nan 1st!; num: " + numerator + ";denom:" + denom + "; for topic t:");
					for (int d=0; d<numDocs; d++) {
						System.out.print("," + prob_topic_given_doc[d][t]);
					}
					System.exit(1);
				}
			} // end of updating n_z
			System.out.print(i);
			for (int t=0; t<this.numTopics; t++) {
				System.out.print("," + n_z[t]);
			}
			System.out.println("");
			for (int t=0; t<this.numTopics; t++) {
				System.out.print("," + prob_topic_given_doc[0][t]);
			}
			System.out.println("");
			//System.exit(1);
			// end of M-step
			
			// computes L (log likelihood) of the content
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
			System.out.println("L (content): " + currentL);
			 
			// computes L of the links
			double curLeft = 0;
			double curRight = 0;
			for (int d=0; d<numDocs; d++) {
				for (int d2=0; d2<numDocs; d2++) {
					if (d==d2) {
						continue;
					}
					String key = String.valueOf(d) + "_" + String.valueOf(d2);
					if (a.containsKey(key)) {
						double coef = a.get(key);
						
						double inside = 0;
						for (int t=0; t<this.numTopics; t++) {
							inside += this.q_d_dprime_z[d][d2][t]*Math.log((this.prob_topic_given_doc[d][t] * this.prob_topic_given_doc[d2][t] * this.n_z[t]) / this.q_d_dprime_z[d][d2][t]);
						}
						curLeft += (coef * inside);
					}
					
					for (int t=0; t<this.numTopics; t++) {
						curRight += (this.prob_topic_given_doc[d][t] * this.prob_topic_given_doc[d2][t] * this.n_z[t]);
					}
					/*
					for (int t=0; t<this.numTopics; t++) {
						curLeft += this.a_d_dprime.get(d)[d2]*this.q_d_dprime_z[d][d2][t]*Math.log((this.prob_topic_given_doc[d][t] * this.prob_topic_given_doc[d2][t] * this.n_z[t]) / this.q_d_dprime_z[d][d2][t]);
						curRight += (this.prob_topic_given_doc[d][t] * this.prob_topic_given_doc[d2][t] * this.n_z[t]);
					}
					*/
				}
			}
			currentL = (curLeft/2) - (curRight/2);
			System.out.println("L (links): " + currentL);
			
		} // end of EM
		

		
		// prints P(t|d)
		int docNum = 0; //docNameToID.get("P05-1022");
		System.out.println("docNum: " + docNum);
		Map<Integer, Double> topics = new HashMap<Integer, Double>();
		for (int t=0; t<this.numTopics; t++) {
			topics.put(t, prob_topic_given_doc[docNum][t]);
		}
		Iterator it = sortByValueDescending(topics).keySet().iterator();
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

			it = sortByValueDescending(wordScores).keySet().iterator();
			for (int i=0; i<15 && it.hasNext(); i++) {
				Integer wordID = (Integer)it.next();
				System.out.print(IDToWord.get(wordID) + " "); //= " + wordScores.get(wordID));
			}
			System.out.println("");
		}
	}


	private int getPoisson(int d, int d2) {
		
		int ret = 0;
		double param = 0.0;
		for (int t=0; t<this.numTopics; t++) {
			if (Double.isNaN(prob_topic_given_doc[d][t]) || Double.isNaN(prob_topic_given_doc[d2][t]) || Double.isNaN(this.n_z[t])) {
				continue;
			}
			param += prob_topic_given_doc[d][t] * prob_topic_given_doc[d2][t] * this.n_z[t];
			if (Double.isNaN(param)) {
				System.out.println(prob_topic_given_doc[d][t] + " " + prob_topic_given_doc[d2][t] + " " + this.n_z[t]);
			}
		}
		if (param > 0) {
			try {
				PoissonDistribution pd = new PoissonDistribution(param);
				ret = pd.sample();
			} catch (Exception e) {
				System.out.println("failed to get it with param:" + param);
				//System.exit(1);
			}
		}
		//System.out.println(param); // + " " + prob_topic_given_doc[d][t]  + " " + prob_topic_given_doc[d2][t] + " " + this.n_z[t]);

		//System.out.println("mean: " + param + "; sample = " + ret);
		return ret;
	}


	public void saveModel(String pmtlmObject) throws IOException {
		Map<String, Map<String, Double>> docToDocProbabilities = new HashMap<String, Map<String, Double>>();
		for (int d=0; d<numDocs; d++) {
			
			String d1Name = this.docIDToName.get(d);
			Map<String, Double> docProbs = new HashMap<String, Double>();
			
			for (int d2=0; d2<numDocs; d2++) {
				if (d==d2) {
					continue;
				}
				String d2Name = this.docIDToName.get(d2);
				
				double param = 0.0;
				for (int t=0; t<this.numTopics; t++) {
					if (Double.isNaN(prob_topic_given_doc[d][t]) || Double.isNaN(prob_topic_given_doc[d2][t]) || Double.isNaN(this.n_z[t])) {
						continue;
					}
					param += prob_topic_given_doc[d][t] * prob_topic_given_doc[d2][t] * this.n_z[t];
					if (Double.isNaN(param)) {
						System.out.println(prob_topic_given_doc[d][t] + " " + prob_topic_given_doc[d2][t] + " " + this.n_z[t]);
					}
				}
				docProbs.put(d2Name, param); //this.a_d_dprime.get(d)[d2]);
			}
			
			if (d==0) {
				Iterator it = sortByValueDescending(docProbs).keySet().iterator();
				System.out.println(d1Name + "'s links:");
				while (it.hasNext()) {
					String d2 = (String)it.next();
					System.out.println(d2 + " = " + docProbs.get(d2));
				}
			}
			docToDocProbabilities.put(d1Name, docProbs);
		}
		PMTLMModelObject tmp = new PMTLMModelObject(docToDocProbabilities);
		FileOutputStream fileOut = new FileOutputStream(pmtlmObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
	}

}

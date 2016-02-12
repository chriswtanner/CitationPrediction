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

public class AuthorLinkLDA1 {
	
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/docsLegend.txt";
	//String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/cora-doc.txt";
	//public static String referentialFile = "/Users/christanner/research/projects/CitationFinder/input/all_10k_docs/referential.txt";
	
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
	//Map<String, MetaDoc> docToMeta = new HashMap<String, MetaDoc>();
	Map<String, List<String>> docToAuthors = new HashMap<String, List<String>>();
	
	
	// model variables
	double[][] prob_topic_given_doc;
	double[][] prob_word_given_topic;
	//double[][][] prob_link_given_topic_author;
	Map<Integer, Map<Integer, Map<Integer, Double>>> p_atl = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
	double[][] n_dtWords;
	double[][] n_dtLinks;	
	double[][] n_tw = null;
	//double[][][] n_atl = null;	
	/*
	Set<String> reportNames = new HashSet<String>();
	Set<String> sourceNames = new HashSet<String>();
	Set<String> corpus = new HashSet<String>();
	*/
	
	boolean firstAuthor;
	int numTopics = 50;
	int numDocs;
	int numUniqueWords;
	int numUniqueLinks = 1; // bogus initializing for dividing purposes
	int numUniqueAuthors = 0;
	int numIterations = 2000;
	double plsaAlpha = 0.99;
	double gamma = 2;
    double topicPadding = 0.01; //.5;
    double wordPadding = 0.05; //0.1; // TODO: this gave the best; try 0.1
    double linkPadding = 0.01;
	public AuthorLinkLDA1(double alpha, int numIterations2, int g, boolean fa, String malletInputFile, String trainingFile, String testingFile, String stopFile, String metaFile) throws IOException {
		
		
		// represents the user passed in alpha and # iterations
		if (alpha != 0) {
			this.plsaAlpha = alpha;
			this.numIterations = numIterations2;
			this.gamma = g;
		}
		
		this.firstAuthor = fa;
		this.stopwords = loadList(stopFile);

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
		System.out.println("done reading mallet");
		
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
		}
		
		System.out.println("done reading .training");
		for (Integer reportID : this.trainingReportToSources.keySet()) {
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
		
		numDocs = docs.keySet().size();
		numUniqueWords = wordToID.keySet().size();
		numUniqueLinks = linkToID.keySet().size();
		numUniqueAuthors = authorToID.keySet().size();
		
		prob_topic_given_doc = new double[numDocs][this.numTopics];
		prob_word_given_topic = new double[this.numTopics][this.numUniqueWords];
		//prob_link_given_topic_author = new double[this.numUniqueAuthors][this.numTopics][this.numUniqueLinks];
		Map<Integer, Map<Integer, Map<Integer, Double>>> p_atl = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
		
		System.out.println("numDocs: " + numDocs);
		System.out.println("# unique authors: " + numUniqueAuthors);
		System.out.println("num unique Links: " + numUniqueLinks);
		System.out.println("num unique Words: " + numUniqueWords);
		System.out.println("# unique reports: " + trainingReportToSources.keySet().size());
		
		// debugging: checks if any reports in testing have authors that weren't seen in training
		bin = new BufferedReader(new FileReader(testingFile));
		curLine = "";
		
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String source = st.nextToken(); // it's the 1st token because .training file is source report (due to PMTLM's requirements)
			String report = st.nextToken();
			
			if (firstAuthor) {
				String author = this.docToAuthors.get(report).get(0);
				if (!authorToID.containsKey(author)) {
					System.out.println("we don't have author for report: " + report);
				}
			} else {
				for (String author : this.docToAuthors.get(report)) {
					if (!authorToID.containsKey(author)) {
						System.out.println("we don't have author for report: " + report);
					}	
				}
			}
		}
		System.out.println("done reading .testing");
		Iterator it = sortByValueDescending(authorCounts).keySet().iterator();
		int numAbove50 = 0;
		while (it.hasNext()) {
			String author = (String)it.next();
			System.out.println(author + " = " + authorCounts.get(author));
			if (authorCounts.get(author) >= 50) {
				numAbove50++;
			}
		}
		System.out.println("> 50: " + numAbove50 + " of " + authorCounts.keySet().size());
		//System.exit(1);
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
	public void runEM() {

		System.out.println("entered EM");
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
		
		/*
		// fills in the initial vals for prob_word_given_topic
		for (int a=0; a<this.numUniqueAuthors; a++) {
			
			Map<Integer, Map<Integer, Double>> p_tl = new HashMap<Integer, Map<Integer, Double>>();
			for (int t=0; t<this.numTopics; t++) {
				
				Map<Integer, Double> p_l = new HashMap<Integer, Double>();
				double total = 0;
				for (int l=0; l<numUniqueLinks; l++) {
					// prob_link_given_topic_author[a][t][l] = initLinkGivenTopicProb*(1 + rand.nextDouble()*0.05);

					double val = initLinkGivenTopicProb*(1 + rand.nextDouble()*0.05);
					p_l.put(l, val);
					total += val; 
					
					//total += prob_link_given_topic_author[a][t][l];
				}
				// normalizes
				for (int l=0; l<numUniqueLinks; l++) {
					
					p_l.put(l, p_l.get(l) / total);
					//prob_link_given_topic_author[a][t][l] /= total;
				}
				p_tl.put(t, p_l);
			}
			
			p_atl.put(a, p_tl);
		}
		*/
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
			
			
			System.out.println("starting E-step: " + i);
			// performs the E-step
			//********************
			// iterates over all docs and words therein
			n_dtWords = new double[numDocs][this.numTopics];
			n_dtLinks = new double[numDocs][this.numTopics];
			
			n_tw = new double[this.numTopics][this.numUniqueWords];
			//n_atl = new double[this.numUniqueAuthors][this.numTopics][this.numUniqueLinks];
			Map<Integer, Map<Integer, Map<Integer, Double>>> atl = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
			int numdocscontaining = 0;
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

						n_dtWords[d][t] += q[t];
						n_tw[t][wordID] += q[t];
					}
				}
				
				// adds all link tokens
				if (this.trainingReportToSources.containsKey(d)) {
					numdocscontaining++;
					for (Integer linkID : this.trainingReportToSources.get(d)) {
						
						// only the 1st author contributes
						if (firstAuthor) {
							double p = 0;
							double[] q = new double[this.numTopics];
							for (int t=0; t<this.numTopics; t++) {
								String reportName = this.docIDToName.get(d);
								String author = this.docToAuthors.get(reportName).get(0);
								if (this.authorToID.containsKey(author)) {
									int authorID = this.authorToID.get(author);
									//q[t] = prob_topic_given_doc[d][t] * prob_link_given_topic_author[authorID][t][linkID];
									
									// saves memory by preventing us from initializing EVERY a*t*l combination to uniform values!
									if (i==0) {
										q[t] = prob_topic_given_doc[d][t];
										p += q[t];
									} else {
									
										if (p_atl.get(authorID).get(t).containsKey(linkID)) {
											q[t] = prob_topic_given_doc[d][t] * p_atl.get(authorID).get(t).get(linkID);
											p += q[t];
										}
									}
								}
							}
							// normalizes over all topics
							for (int t=0; t<this.numTopics; t++) {
								// represents the average topic-link prob, after averaging over all topics;
								// but we don't know if it's based on 1 author's vals or the avg across all author's of the given report

								if (p > 0) { // ensures we don't divide by 0
									q[t] /= p; 
									n_dtLinks[d][t] += q[t];
									
									String reportName = this.docIDToName.get(d);
									String author = this.docToAuthors.get(reportName).get(0);
									// pointless check, because p > 0 ensures that we've seen this author already
									if (this.authorToID.containsKey(author)) {
										int authorID = this.authorToID.get(author);
										
										Map<Integer, Map<Integer, Double>> tmpTL = new HashMap<Integer, Map<Integer, Double>>();
										if (atl.containsKey(authorID)) {
											tmpTL = atl.get(authorID);
										}
										
										Map<Integer, Double> tmpL = new HashMap<Integer, Double>();
										if (tmpTL.containsKey(t)) {
											tmpL = tmpTL.get(t);
										}

										double val = 0;
										if (tmpL.containsKey(linkID)) {
											val = tmpL.get(linkID);
										}
										
										val += q[t];
										tmpL.put(linkID, val);
										
										tmpTL.put(t, tmpL);
										atl.put(authorID, tmpTL);
										//n_atl[authorID][t][linkID] += q[t];
									}
								}
							} // end of normalizing over topics
							
						// we want to look at all authors on the given paper
						} else {
							double[] a_p = new double[this.numUniqueAuthors];
							double[][] a_q = new double[this.numUniqueAuthors][this.numTopics];
							double p = 0; // for doc-topic dist
							double[] q = new double[this.numTopics]; // for doc-topic dist
							for (int t=0; t<this.numTopics; t++) {
								double totalProb = 0;
								String reportName = this.docIDToName.get(d);
								int numAuthorsFound = 0;
								for (String author : this.docToAuthors.get(reportName)) {
									if (this.authorToID.containsKey(author)) {
										int authorID = this.authorToID.get(author);
										
										double prob = prob_topic_given_doc[d][t];
										if (i == 0) {
											a_q[authorID][t] = prob;
											a_p[authorID] += prob;
											
											totalProb += prob;
											numAuthorsFound++;
										} else {
											if (p_atl.get(authorID).get(t).containsKey(linkID)) {
	
												prob = prob_topic_given_doc[d][t] * p_atl.get(authorID).get(t).get(linkID);
	
												//double prob = prob_topic_given_doc[d][t] * prob_link_given_topic_author[authorID][t][linkID];
												a_q[authorID][t] = prob;
												a_p[authorID] += prob;
												
												totalProb += prob;
												numAuthorsFound++;
											}
										}
									}
								}
								//System.out.println("numauthorsfound: " + numAuthorsFound);
								if (numAuthorsFound > 0) {
									double avgProb = totalProb / (double)numAuthorsFound;
									q[t] = avgProb;
									p += q[t];
								}
							} // end of going through all topics
							
							// normalizes over all topics
							for (int t=0; t<this.numTopics; t++) {
							
								if (p > 0) { // ensures we don't divide by 0
									q[t] /= p;
									n_dtLinks[d][t] += q[t];
									
									String reportName = this.docIDToName.get(d);
									for (String author : this.docToAuthors.get(reportName)) {
										if (this.authorToID.containsKey(author)) {
											int authorID = this.authorToID.get(author);
											
											a_q[authorID][t] /= a_p[authorID];
											
											
											Map<Integer, Map<Integer, Double>> tmpTL = new HashMap<Integer, Map<Integer, Double>>();
											if (atl.containsKey(authorID)) {
												tmpTL = atl.get(authorID);
											}
											
											Map<Integer, Double> tmpL = new HashMap<Integer, Double>();
											if (tmpTL.containsKey(t)) {
												tmpL = tmpTL.get(t);
											}

											double val = 0;
											if (tmpL.containsKey(linkID)) {
												val = tmpL.get(linkID);
											}
											
											val += a_q[authorID][t];
											tmpL.put(linkID, val);
											
											tmpTL.put(t, tmpL);
											atl.put(authorID, tmpTL);
											
											//n_atl[authorID][t][linkID] += a_q[authorID][t];
										}
									}
								}
							}
						} // end of going through all topics
					} // end of going through all links
				} // end of checking if we are a report; only reports contain links, where as for the word token part of E-step, all docs have words
			} // end of E-step
			//System.out.println("numdocsasreports: " + numdocscontaining);
			// performs the M-step
			//********************
			System.out.println("starting M-step: " + i);
			p_atl.clear(); // NOTE: this is vital; we clear space, as we don't need this during the M-step. 
			// but momentarily, we update all of p_atl
			
			// updates P(Z|D)
			for (int d=0; d<numDocs; d++) {
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
			// accumulates counts which will be used for the back-off smoothing
			double[][] topicLink = new double[this.numTopics][this.numUniqueLinks];
			double[] topics = new double[this.numTopics];
			
			for (int a=0; a<this.numUniqueAuthors; a++) {
				for (int t=0; t<this.numTopics; t++) {
					
					Iterator it = sortByValueDescending(atl.get(a).get(t)).keySet().iterator();
					int numLinks = atl.get(a).get(t).keySet().size();
					int i2=0;
					while (it.hasNext() && i2 < (double)(1 * numLinks)) {
						int l = (Integer)it.next();
						topicLink[t][l] += atl.get(a).get(t).get(l);
						topics[t] += atl.get(a).get(t).get(l);
						i2++;
					}
					/*
					for (int l=0; l<numUniqueLinks; l++) {
						topicLink[t][l] += n_atl[a][t][l];
						topics[t] += n_atl[a][t][l];
					}
					*/
				}
			}
			
			
			for (int a=0; a<this.numUniqueAuthors; a++) {
				
				Map<Integer, Map<Integer, Double>> p_tl = new HashMap<Integer, Map<Integer, Double>>();
				for (int t=0; t<this.numTopics; t++) {
					
					double sum = 0;
					Map<Integer, Double> lTmp = new HashMap<Integer, Double>();
					for (int l=0; l<numUniqueLinks; l++) {
						
						double linkProb = 0;
						if (atl.get(a).get(t).containsKey(l)) {
							linkProb = linkPadding + atl.get(a).get(t).get(l) + this.gamma*(topicLink[t][l]/topics[t]);
						} else {
							linkProb = linkPadding + this.gamma*(topicLink[t][l]/topics[t]);
						}
						sum +=  linkProb;
						lTmp.put(l, linkProb);
					}
					
					Iterator it = sortByValueDescending(lTmp).keySet().iterator();
					int numLinks = lTmp.keySet().size();
					int i2=0;
					//System.out.println("author " + a + " t: " + t + " has # links: " + numLinks);
					while (it.hasNext() && i2 < (double)(.5 * numLinks)) {
						int l = (Integer)it.next();
						//System.out.print("\t" + lTmp.get(l));
						sum += lTmp.get(l);
						i2++;
					}
					//System.out.println("we filled in " + i2 + " out of " + numLinks  + "; #unique links: " + numUniqueLinks);
					
					// normalizes
					Map<Integer, Double> p_l = new HashMap<Integer, Double>();
					it = sortByValueDescending(lTmp).keySet().iterator();
					i2=0;
					while (it.hasNext() && i2 < (double)(.5 * numLinks)) {
						int l = (Integer)it.next();
						p_l.put(l, (linkPadding + lTmp.get(l)) / sum);
						i2++;
					}
					p_tl.put(t, p_l);
					
					
					/* my new way; just over every link, which fills up too much memory
					// normalizes
					Map<Integer, Double> p_l = new HashMap<Integer, Double>();
					for (int l=0; l<numUniqueLinks; l++) {
						double linkProb = lTmp.get(l) / sum;
						p_l.put(l, linkProb);
					}
					p_tl.put(t, p_l);
					*/
					
					/* old way
					for (int l=0; l<numUniqueLinks; l++) {
						sum += (n_atl[a][t][l] + this.gamma*(topicLink[t][l]/topics[t]));
					}
					for (int l=0; l<numUniqueLinks; l++) {
						prob_link_given_topic_author[a][t][l] = (n_atl[a][t][l] + this.gamma*(topicLink[t][l]/topics[t])) / sum;
					}
					*/
				}
				p_atl.put(a, p_tl);
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
					String reportName = this.docIDToName.get(d);
					for (Integer linkID : this.trainingReportToSources.get(d)) {

						double sum = 0;
						for (int t=0; t<this.numTopics; t++) {
							
							
							if (firstAuthor) {
								String author = this.docToAuthors.get(reportName).get(0);
								if (this.authorToID.containsKey(author)) {
									
									if (p_atl.get(author).get(t).containsKey(linkID)) {
										sum += p_atl.get(author).get(t).get(linkID);
										//sum += prob_link_given_topic_author[this.authorToID.get(author)][t][linkID];
									}
								}
							} else {
								double total = 0;
								int numAuthorsFound = 0;
								for (String author : this.docToAuthors.get(reportName)) {
									if (this.authorToID.containsKey(author)) {
										if (p_atl.get(authorToID.get(author)).get(t).containsKey(linkID)) {
											total += p_atl.get(authorToID.get(author)).get(t).get(linkID);
											//total += prob_link_given_topic_author[this.authorToID.get(author)][t][linkID];
											numAuthorsFound++;
										}

									}
								}
								if (numAuthorsFound > 0) {	
									double avg = total / (double)numAuthorsFound;
									sum += avg;
								}
							}
						}
						if (sum > 0) {
							currentL += Math.log(sum);
						}
					}
				}
			}
			System.out.println("authorLinkLDA1 (" + i + ") L: " + currentL);
		} // end of EM	
	}
	
	public void printTopics(int docNum) {
		// prints P(t|d)
		//int docNum = 0; //docNameToID.get("P05-1022");
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
	
	public void saveModel(String lObject) throws IOException {
		System.out.println("saving model...");
		Map<String, Map<Integer, Map<String, Double>>> authorTopicToLinkProbs = new HashMap<String, Map<Integer, Map<String, Double>>>();
		
		for (int a=0; a<this.numUniqueAuthors; a++) {
			Map<Integer, Map<String, Double>> topicToLinkProbabilities = new HashMap<Integer, Map<String, Double>>();
			for (int t=0; t<this.numTopics; t++) {
				Map<String, Double> linkProbs = new HashMap<String, Double>();
				int numValid = 0;
				for (int l=0; l<numUniqueLinks; l++) {
					
					if (p_atl.get(a).get(t).containsKey(l)) {
						if (p_atl.get(a).get(t).get(l) > 0.0001) {
							linkProbs.put(this.IDToLink.get(l), p_atl.get(a).get(t).get(l));
							numValid++;
						}
					}
					
					/*
					if (prob_link_given_topic_author[a][t][l] > 0.0001) {
						linkProbs.put(this.IDToLink.get(l), prob_link_given_topic_author[a][t][l]);
						numValid++;
					}
					*/
				}
				System.out.println("author " + a + " topic: " + t + " had " + numValid + " valid links");
				topicToLinkProbabilities.put(t, linkProbs);
			}
			
			authorTopicToLinkProbs.put(this.IDToAuthor.get(a), topicToLinkProbabilities);
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
		
		System.out.println("writing to disk...");
		AuthorLinkLDA1ModelObject tmp = new AuthorLinkLDA1ModelObject(linkToID, IDToLink, authorTopicToLinkProbs, topicToWordProbabilities, docToTopicProbabilities);
		
		FileOutputStream fileOut = new FileOutputStream(lObject);
		ObjectOutputStream out = new ObjectOutputStream(fileOut);
		out.writeObject(tmp);
		out.close();
		fileOut.close();
		System.out.println("done");
	}
	
	/*
	private static void loadReferential(String ref) throws IOException {
		BufferedReader bin = new BufferedReader(new FileReader(ref));

		allReportToSources = new HashMap<String, Set<String>>();
		
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			// ensures we have report ==> source
			if (st.countTokens() != 2) {
				continue;
			}
			
			String report = st.nextToken();
			String source = st.nextToken();
						
			// skip over reports/sources that we don't have both meta-data and mallet info for
			if (!docIDToMeta.containsKey(report) || !docIDToMeta.containsKey(source) || !malletDocs.contains(report) || !malletDocs.contains(source)) {
				System.out.println("skipping " + report + " and " + source);
				continue;
			}
			
			// updates our map of Report -> {sources}
			Set<String> curSources = new HashSet<String>();
			if (allReportToSources.containsKey(report)) {
				curSources = allReportToSources.get(report);
			}
			if (!report.equals(source)) {
				curSources.add(source);
			}
			allReportToSources.put(report, curSources);
		}
		
		// now goes through in reverse-chronological order (i.e., starting with 2013) to
		// fill in the docs that we'll use; this is because we want to give our reports
		// a good chance of having previously cited papers for author information
		Map<String, Integer> authorReportCount = new HashMap<String, Integer>();
		for (String auth : authorToReports.keySet()) {
			authorReportCount.put(auth, authorToReports.get(auth).size());
		}
		
		//Random rand = new Random();
		int numReports = 0;
		//while (allDocuments.size() < maxDocsToCompriseCorpus) {
		java.util.Iterator it = sortByValueDescending(authorReportCount).keySet().iterator();
		while (it.hasNext()) {
			// pick a random author
			//int r = rand.nextInt(authorToReports.keySet().size());
			//java.util.Iterator<String> it = authorToReports.keySet().iterator();
			int i=0;
			String chosenAuthor = (String) it.next(); //";
			if (!chosenAuthor.equals("")) {
				
				System.out.println("adding author: " + chosenAuthor + " who has " + authorToReports.get(chosenAuthor).size() + " reports");
				// process all reports by the given author
				for (String report : authorToReports.get(chosenAuthor)) {
					
					// if the report hasn't been processed already; pointless because allDocuments is a Set, not List
					if (!allDocuments.contains(report)) {
						if (allReportToSources.containsKey(report)) {
							allDocuments.add(report);
							
							numReports++;
							for (String source : allReportToSources.get(report)) {
								allDocuments.add(source);
								
								// updates source -> <reports>
								Set<String> tmpReports = new HashSet<String>();
								if (sourceToReports.containsKey(source)) {
									tmpReports = sourceToReports.get(source);
								}
								tmpReports.add(report);
								sourceToReports.put(source, tmpReports);
							}
							
							reportToSources.put(report, allReportToSources.get(report));
							
							// checks if we've met our max for # of docs in our to-be corpus
							if (allDocuments.size() >= (1.5*(double)maxDocsToCompriseCorpus)) {
								System.out.println("numReports: " + numReports);
								return;
							}
						}
					}
				}
			}
		}

		System.out.println("finished; numReportS: " + numReports);
	}
	*/
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;


public class CitationEngine {
	
	// PLSA's input files (which are the same that mallet uses for LDA (via LDADriver)).
	// this way, we ensure that our docs' words are exactly the same
	String stopwords = "/Users/christanner/research/projects/CitationFinder/input/stopwords.txt";
	
	boolean firstAuthor = false;
	
	String docsPath = "";
	String trainingCitesFile = "";
	String testingCitesFile = "";
	String topicsOut = "";
	String metaFile = "";
	String ldaInput = "";
	
	List<Integer> numSourcesCutoffs = null;
	Set<String> candidateSources = new HashSet<String>();
	
	boolean bayesScoring = true; // true represents our gold standard way; false represents PMTLM paper's way
	
	// stores all sources that were ever cited within TRAINING OR TEST
	// (this is legit, as it's okay to make a bipartite graph apriori)
	Set<String> sources = new HashSet<String>(); 
	
	TopicModelObject tmo = null;
	LinkLDAModelObject lmo = null;
	AuthorLinkLDA1ModelObject amo1 = null;
	AuthorLinkLDA2ModelObject amo2 = null;
	AuthorLinkLDA3ModelObject amo3 = null;
	PMTLMModelObject pmo = null;
	
	int numTopics = 0;
	
	Map<String, Set<String>> reportToTestingSources = new HashMap<String, Set<String>>();
	Map<String, Integer> citedCounts = new HashMap<String, Integer>(); // counts how many times the key doc was CITED BY another doc in the training
	Map<String, MetaDoc> docToMeta = new HashMap<String, MetaDoc>();
	
	double alpha = 0; // only used for writing out multiple runs
	
	Set<String> allReports = new HashSet<String>();
	
	// only used due to pmtlmData model, in which case this stores the docs we should ignore when ranking each doc for a given report
	// (i.e., ignore the docs that were golden sources during training for the PMTLM model,
	// and for comparison sake, we don't want to evaluate ourselves on them either)
	Map<String, Set<String>> reportToTrainingSources = new HashMap<String, Set<String>>();
	Set<String> trainingSources = new HashSet<String>();
	
	Map<String, List<String>> reportToRankedSources = new HashMap<String, List<String>>(); // stores the ranked sources for each report
	//Map<String, Double> globalSourceProbs = new HashMap<String, Double>();
	Double[] topicSums = null;
	
	Set<Integer> worstTopics = new HashSet<Integer>();
	//double[] topicWeights;
	int totalCitations = 0;
	
	// tmp, just to test if the prevCIted feature could actually help us... so we print stuff and need this:
	public static Map<String, Set<String>> authorToReports = new HashMap<String, Set<String>>();
	
	public CitationEngine(double alpha, String scoringMethod, String docsPath, String trainingCitesFile, String testingCitesFile, String metaFile, String modelInput, String topicsOut, List<Integer> sourcesCutoffs, String malletInputFile) throws IOException, ClassNotFoundException {
		this.docsPath = docsPath;
		this.trainingCitesFile = trainingCitesFile;
		this.testingCitesFile = testingCitesFile;
		this.metaFile = metaFile;
		this.ldaInput = modelInput;
		this.numSourcesCutoffs = sourcesCutoffs;
		this.topicsOut = topicsOut;
		this.alpha = alpha;
		// stores word -> {docs} just for the sake of measuring topic coherence (we need to know how many docs each word appears in)
		Map<String, Set<String>> wordToDocs = new HashMap<String, Set<String>>();
		BufferedReader bin = new BufferedReader(new FileReader(malletInputFile));

		String curLine = "";
		Set<String> docs = new HashSet<String>();
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();
			docs.add(docName);
			st.nextToken(); // pointless token

			Map<Integer, Integer> docWordCount = new HashMap<Integer, Integer>();

			while (st.hasMoreTokens()) {
				String curWord = st.nextToken();
				Set<String> curDocs = new HashSet<String>();
				if (wordToDocs.containsKey(curWord)) {
					curDocs = wordToDocs.get(curWord);
				}
				curDocs.add(docName);
				wordToDocs.put(curWord, curDocs);
			}
		}
		int numDocs = docs.size();
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
		
		// (1) loads the training sources
		// (2) simultaneously creates citationCounts map
		// (3) adds to the corpus collection, which will be used
		// as a superset for candidate sources to rank
		// (4) adds the sources to our bipartite set of potential sources
		citedCounts = new HashMap<String, Integer>();
		reportToTrainingSources = new HashMap<String, Set<String>>();
		bin = new BufferedReader(new FileReader(this.trainingCitesFile));
		curLine = "";
		Set<String> allDocs = new HashSet<String>();
		
		while ((curLine = bin.readLine())!=null) {
			String[] tokens = curLine.split(" ");
			String report = tokens[1];
			String source = tokens[0];
			
			allDocs.add(report);
			allDocs.add(source);
			Set<String> tmp = new HashSet<String>();
			if (reportToTrainingSources.containsKey(report)) {
				tmp = reportToTrainingSources.get(report);
			}
			tmp.add(source);
			reportToTrainingSources.put(report, tmp);
			
			// updates the # of times the source was cited during training
			int count = 0;
			if (citedCounts.containsKey(source)) {
				count = citedCounts.get(source);
			}
			count++;
			citedCounts.put(source, count);
			
			// updates totalCitations
			this.totalCitations++;
			
			// adds to corpus
			//candidateSources.add(report);
			candidateSources.add(source);
			
			// adds to bipartite sources
			sources.add(source);
			
			trainingSources.add(source);
			
			allReports.add(report);
		}
		
		// (1) reads in the testing/referential file's golden answers
		// (2) adds to the corpus collection, which will be used
		// as a superset for candidate sources to rank
		// (3) adds the sources to our bipartite set of potential sources
		bin = new BufferedReader(new FileReader(testingCitesFile));
		
		Set<String> sourcesMissing = new HashSet<String>();
		Set<String> a = new HashSet<String>();
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String source = st.nextToken();
			String report = st.nextToken();
			
			allDocs.add(report);
			allDocs.add(source);
			
			Set<String> curSources = new HashSet<String>();
			if (reportToTestingSources.containsKey(report)) {
				curSources = reportToTestingSources.get(report);
			}
			curSources.add(source);
			reportToTestingSources.put(report, curSources);
			
			// adds to corpus
			//candidateSources.add(report);
			candidateSources.add(source);
			
			// adds to bipartite sources
			sources.add(source);
			
			a.add(source);
			
			if (!trainingSources.contains(source)) {
				sourcesMissing.add(source);
			}
			
			
			allReports.add(report);
		}
		
		System.out.println("alldocs: " + allDocs.size());
		System.out.println("testing saw " + a.size() + " unique sources; " + sourcesMissing.size() + " were missing");
		System.out.println("# reports to test: " + reportToTestingSources.keySet().size());
		System.out.println("size of corpus (aka currently candidateSources unless we curtail it to just bipartite sources or ones < current year): " + candidateSources.size());
		System.out.println("# of all sources (training and test): " + sources.size());
		System.out.println("metafile: " + metaFile);
		//System.exit(1);
		// if we have a metadocs file, let's store its info
		if (!metaFile.equals("")) {
			// loads the MetaDocs
			docToMeta = new HashMap<String, MetaDoc>();
			bin = new BufferedReader(new FileReader(this.metaFile));
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
				docToMeta.put(docID, md);
				//System.out.println("adding meta");
				
				// updates each author in this report to include this report
				for (String author : authorTokens) {
					Set<String> tmpReports = new HashSet<String>();
					if (authorToReports.containsKey(author)) {
						tmpReports = authorToReports.get(author);
					}

					tmpReports.add(docID);
					authorToReports.put(author, tmpReports);

					if (author.equals("charniak, eugene")) {
						System.out.println("eugene's " + docID + " paper IS in our reports ***");
					}
				}
			}
			
			System.out.println("meta doc size: " + docToMeta.keySet().size());
			for (String doc : reportToTestingSources.keySet()) {
				if (!docToMeta.containsKey(doc)) {
					System.out.println("metadoc not found for report: " + doc);
				}
			}
			
			for (String doc : candidateSources) {
				if (!docToMeta.containsKey(doc)) {
					System.out.println("metadoc not found for candidate source: " + doc);
				}
			}
		}
		

		ObjectInputStream in;
		
		
		// determines the 'coherence' of each topic, which will be used for calculating the score of each report-source
		//Map<Integer, Double> topicToScore = new HashMap<Integer, Double>();
		in = new ObjectInputStream(new FileInputStream(modelInput));
		int numBadTopics = 0;
		if (scoringMethod.equals("bayesian")) {
			tmo = (TopicModelObject) in.readObject();
			//topicToScore = getTopicScores(tmo.topicToWordProbabilities, wordToDocs, numDocs);
			this.numTopics = tmo.topicToWordProbabilities.keySet().size();
			numBadTopics = (int) Math.floor(tmo.topicToWordProbabilities.keySet().size() * .1);
		} else if (scoringMethod.equals("linkLDA")) {
			lmo = (LinkLDAModelObject) in.readObject();
			//topicToScore = getTopicScores(lmo.topicToWordProbabilities, wordToDocs, numDocs);
			this.numTopics = lmo.topicToWordProbabilities.keySet().size();
			numBadTopics = (int) Math.floor(lmo.topicToWordProbabilities.keySet().size() * .1);
		} else if (scoringMethod.equals("authorLinkLDA1")) {
			amo1 = (AuthorLinkLDA1ModelObject)in.readObject();
			//topicToScore = getTopicScores(amo1.topicToWordProbabilities, wordToDocs, numDocs);
			this.numTopics = amo1.topicToWordProbabilities.keySet().size();
			numBadTopics = (int) Math.floor(amo1.topicToWordProbabilities.keySet().size() * .1);
		} else if (scoringMethod.equals("authorLinkLDA2")) {
			amo2 = (AuthorLinkLDA2ModelObject) in.readObject();
			//topicToScore = getTopicScores(lmo.topicToWordProbabilities, wordToDocs, numDocs);
			this.numTopics = amo2.topicToWordProbabilities.keySet().size();
		} else if (scoringMethod.equals("authorLinkLDA3")) {
			amo3 = (AuthorLinkLDA3ModelObject) in.readObject();
			//topicToScore = getTopicScores(lmo.topicToWordProbabilities, wordToDocs, numDocs);
			this.numTopics = amo3.topicToWordProbabilities.keySet().size();
		}
		//worstTopics = new HashSet<Integer>();
		//topicWeights = new double[this.numTopics];
		
		/*
		System.out.println("# topicsss!!" + this.numTopics);
		Iterator it = sortByValueAscending(topicToScore).keySet().iterator();
		
		int i=0;
		double best = -999999;
		double worst = 999999;
		while (it.hasNext()) {
			int t = (Integer)it.next();
			if (i<numBadTopics) {
				worstTopics.add(t);
			}
			double score = topicToScore.get(t);
			if (score > best) {
				best = score;
			}
			if (score < worst) {
				worst = score;
			}
			i++;
		}
		System.out.println("best score: " + best + " worst: " + worst);
		System.out.println("worst topics: " + worstTopics);
		double range = Math.abs(best - worst);
		
		it = sortByValueDescending(topicToScore).keySet().iterator();
		while (it.hasNext()) {
			int t = (Integer)it.next();
			double d = 1 - (Math.abs(best - topicToScore.get(t))/range);
			topicWeights[t] = d;
			System.out.println("score: " + topicToScore.get(t) + " => " + d);
		}
		*/
		
		// reads in the TopicModelObject (i.e., mallet's LDA model's statistics like P(W|Z), P(Z|D))
		System.out.println("# metadocs: " + docToMeta.keySet().size());
		try {
			System.out.println("*** candidate sources size: " + candidateSources.size());
			
			in = new ObjectInputStream(new FileInputStream(modelInput));
			
			if (scoringMethod.equals("bayesian")) {
				tmo = (TopicModelObject) in.readObject();
				this.numTopics = tmo.topicToWordProbabilities.keySet().size();
				
				// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
				System.out.println("* preprocessing P(S|Z)...");
				topicSums = new Double[this.numTopics];
				for (int t=0; t<this.numTopics; t++) {
					double totalSum = 0.0;
					
					if (worstTopics.contains(t)) {
						//continue;
					}
					for (String candidateDoc : candidateSources /*sources*/) {
						
						double prob_source = 0.01;
						if (this.citedCounts.containsKey(candidateDoc)) {
							prob_source += (double)this.citedCounts.get(candidateDoc);
						}
						prob_source /= (double)this.totalCitations; //1; // uniform
						double prob_topic_given_source = tmo.docToTopicProbabilities.get(candidateDoc)[t];
						totalSum += (prob_topic_given_source * prob_source);
					}
					topicSums[t] = totalSum;
					//System.out.println("topicSums[" + t + "]:" + topicSums[t]);
				}
				System.out.println("done!");
				System.out.println("*** topicSums[0] = " + topicSums[0]);
			} else if (scoringMethod.equals("linkLDA")) {
				
				lmo = (LinkLDAModelObject) in.readObject();
				this.numTopics = lmo.topicToLinkProbabilities.keySet().size();
				
				
				// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
				System.out.println("* preprocessing P(S|Z)...");
				topicSums = new Double[this.numTopics];
				for (int t=0; t<this.numTopics; t++) {
					
					
					double totalSum = 0.0;
					if (worstTopics.contains(t)) {
						//continue;
					}
					for (String candidateDoc : candidateSources) {
						
						double prob_source = 0.01;
						if (this.citedCounts.containsKey(candidateDoc)) {
							prob_source += (double)this.citedCounts.get(candidateDoc);
						}
						prob_source /= (double)this.totalCitations; //1; // uniform
						double prob_topic_given_source = lmo.docToTopicProbabilities.get(candidateDoc)[t];
						totalSum += (prob_topic_given_source * prob_source);
					}
					topicSums[t] = totalSum;
					System.out.println("topicSums[" + t + "]:" + topicSums[t]);
				}
				System.out.println("done!");
				
			} else if (scoringMethod.equals("authorLinkLDA1")) {
				amo1 = (AuthorLinkLDA1ModelObject)in.readObject();
				this.numTopics = amo1.topicToWordProbabilities.keySet().size();
				
			} else if (scoringMethod.equals("authorLinkLDA2")) {
				amo2 = (AuthorLinkLDA2ModelObject) in.readObject();
				this.numTopics = amo2.topicToLinkProbabilities.keySet().size();
			} else if (scoringMethod.equals("authorLinkLDA3")) {
				amo3 = (AuthorLinkLDA3ModelObject) in.readObject();
				this.numTopics = amo3.topicToLinkProbabilities.keySet().size();
			}
			
			
			in.close();
		} catch (IOException sa) {
			sa.printStackTrace();
			return;
		} catch (ClassNotFoundException c) {
			System.out.println("LDAObject class not found");
			c.printStackTrace();
			return;
		}
		/*
		else { // end of checking if our model is lda/plsa instead of pmmodel
			in = new ObjectInputStream(new FileInputStream(modelInput));
			try {
				pmo = (PMTLMModelObject) in.readObject();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		*/
		
		
		// now ranks the sources for each report

		//globalSourceProbs = new HashMap<String, Double>();

		// this block writes to a file all report-source pair scores so that SVM can use it later
		/*
		BufferedWriter bScores = new BufferedWriter(new FileWriter(scoresOut));
		Set<String> allReports = new HashSet<String>();
		for (String r : reportToTestingSources.keySet()) {
			allReports.add(r);
		}
		for (String r : reportToTrainingSources.keySet()) {
			allReports.add(r);
		}
		Random rand = new Random();
		System.out.println("calculating score for " + allReports.size() + " reports");
		for (String report : reportToTrainingSources.keySet()) {
			for (String candidateDoc : candidateSources) {
				
				// skips evaluating the report itself, along with any gold sources seen during training
				if (candidateDoc.equals(report)) {
					continue;
				}

				// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
				double score = score = getBayesianScore(report, candidateDoc);

				boolean isTrainingSource = false;
				if (this.reportToTrainingSources.get(report).contains(candidateDoc)) {
					isTrainingSource = true;
					bScores.write(report + " " + candidateDoc + " " + score + "\n");
				} else {
					
				}
				
				if (!isTrainingSource && !isTestingSource) {
					// only write out 10% of the bad sources, because we're only going to sample 5x the # of good-training-positive sources anyway
					// so as long as there are at least 
					if (rand.nextDouble() < 0.05) {
						bScores.write(report + " " + candidateDoc + " " + score + "\n");
					}
				} else { // we want to write the testing ones because this is what we want to later eval on, and we want to write out the training ones because
					// this will be used for training
					bScores.write(report + " " + candidateDoc + " " + score + "\n");
				}
			}
		}
		for (String report : allReports) {
		// TODO: decide which way i want it; right now i want to have all scores
		//for (String report : reportToTestingSources.keySet()) {

			// determines P(S|R) for all sources which aren't the report
			for (String candidateDoc : candidateSources) {
				
				
				// skips evaluating the report itself, along with any gold sources seen during training
				if (candidateDoc.equals(report)) {
					continue;
				}

				// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
				double score = score = getBayesianScore(report, candidateDoc);

				boolean isTrainingSource = false;
				if (this.reportToTrainingSources.containsKey(report) && this.reportToTrainingSources.get(report).contains(candidateDoc)) {
					isTrainingSource = true;
				}
				boolean isTestingSource = false;
				if (this.reportToTestingSources.containsKey(report) && this.reportToTestingSources.get(report).contains(candidateDoc)) {
					isTestingSource = true;
				}
				if (!isTrainingSource && !isTestingSource) {
					// only write out 10% of the bad sources, because we're only going to sample 5x the # of good-training-positive sources anyway
					// so as long as there are at least 
					if (rand.nextDouble() < 0.05) {
						bScores.write(report + " " + candidateDoc + " " + score + "\n");
					}
				} else { // we want to write the testing ones because this is what we want to later eval on, and we want to write out the training ones because
					// this will be used for training
					bScores.write(report + " " + candidateDoc + " " + score + "\n");
				}

			}
		}
		bScores.close();
		*/
		// this block calcualtes report-source pair scores again, but for our actual eval
		reportToRankedSources = new HashMap<String, List<String>>();
		for (String report : this.reportToTestingSources.keySet()) {

			// determines P(S|R) for all sources which aren't the report
			Map<String, Double> sourceProbs = new HashMap<String, Double>();
			for (String candidateDoc : candidateSources) {
				// skips evaluating the report itself, along with any gold sources seen during training
				if (candidateDoc.equals(report) || (reportToTrainingSources.containsKey(report) && reportToTrainingSources.get(report).contains(candidateDoc)) /*|| (!sources.contains(candidateDoc))*/) {
					continue;
				}

				// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
				double score = 0;
				if (scoringMethod.equals("bayesian")) {
					score = getBayesianScore(report, candidateDoc);
				} else if (scoringMethod.equals("linkLDA")) {
					score = getLinkLDAScore(report, candidateDoc);
				} else if (scoringMethod.equals("authorLinkLDA1")) {
					score = getAuthorLinkLDA1Score(report, candidateDoc);
				} else if (scoringMethod.equals("authorLinkLDA2")) {
					score = getAuthorLinkLDA2Score(report, candidateDoc);
				} else if (scoringMethod.equals("authorLinkLDA3")) {
					score = getAuthorLinkLDA3Score(report, candidateDoc);
				}
				
				
				//globalSourceProbs.put(report + "_" + candidateDoc, score);

				// was used for my attempt to implement PMTLM model
				// score = pmo.docToDocProbabilities.get(report).get(candidateDoc);
				sourceProbs.put(candidateDoc, score);
			}
			if (report.equals("P89-1018")) {
				System.out.println("*** P89-1018 ranked " + sourceProbs.keySet().size() + " docs");
			}
			//System.out.println("report " + report + " ranked " + sourceProbs.keySet().size() + " docs");
			// sort sourceProbs in decreasing order
			List<String> rankedSources = new ArrayList<String>();
			Iterator it3 = sortByValueDescending(sourceProbs).keySet().iterator();
			while (it3.hasNext()) {
				String source = (String)it3.next();
				rankedSources.add(source);
			}

			reportToRankedSources.put(report, rankedSources);
		}

	}

	// prints the topic info and returns the scores
	private Map<Integer, Double> getTopicScores(Map<Integer, Map<String, Double>> topicToWordProbabilities, Map<String, Set<String>> wordToDocs, int numTotalDocs) throws IOException {
		Map<Integer, Double> topicCoherenceScores = new HashMap<Integer, Double>();
		Map<Integer, Double> topicMIScores = new HashMap<Integer, Double>();
		Map<Integer, List<String>> topicWords = new HashMap<Integer, List<String>>();
		
		for (Integer t : topicToWordProbabilities.keySet()) {
			// gets the top 20 words for the current topic
			List<String> topWords = new ArrayList<String>();
			Iterator it = sortByValueDescending(topicToWordProbabilities.get(t)).keySet().iterator();
			int i=0;
			while (it.hasNext() && i<20) {
				topWords.add((String)it.next());
				i++;
			}
			topicWords.put(t, topWords);
			
			//System.out.println(topWords);
			
			// calculates TC (topic coherence) and MI for all pairwise top 20 words
			double tc = 0;
			double mi = 0;
			for (i=0; i<20; i++) {
				Set<String> w1Docs = wordToDocs.get(topWords.get(i));

				int numW1 = w1Docs.size();
				double probW1 = (double)numW1 / (double)numTotalDocs;
				
				if (numW1 == 0) {
					System.err.println("error: somehow, the word claims to be in 0 docs in our corpus!!");
				}
 				for (int j=i+1; j<20; j++) {
 					Set<String> w2Docs = wordToDocs.get(topWords.get(j));
 					int numJointDocs = 0;
 					for (String w2d : w2Docs) {
 						if (w1Docs.contains(w2d)) {
 							numJointDocs++;
 						}
 					}
 					
 					double probW2 = (double)(wordToDocs.get(topWords.get(i)).size()) / (double)numTotalDocs;
 					double jointProb = 0.0001 + (double)(numJointDocs) / (double)numTotalDocs;
 					tc += Math.log((double)(numJointDocs + 1) / (double)numW1);
 					
 					mi += 1 * Math.log(jointProb / (probW1 * probW2));
				}
			}
			topicCoherenceScores.put(t, tc);
			topicMIScores.put(t, mi);
		}
		
		BufferedWriter bTopics = new BufferedWriter(new FileWriter(topicsOut));
		Iterator it = sortByValueDescending(topicMIScores).keySet().iterator();
		while (it.hasNext()) {
			int t = (Integer)it.next();
			bTopics.write("topic " + t + "; coherence = " + topicCoherenceScores.get(t) + "; MI = " + topicMIScores.get(t) + "\n-------------------------\ntop words:");
			for (String w : topicWords.get(t)) {
				bTopics.write(" " + w);
			}
			bTopics.write("\n\n");
		}
		bTopics.close();
		return topicMIScores; //topicCoherenceScores;
	}

	private double getAuthorLinkLDA1Score(String report, String source) {
		double linkScore = 0.0;

		for (int t=0; t<this.numTopics; t++) {
			
			if (worstTopics.contains(t)) {
				//continue;
			}
			if (this.firstAuthor) {
				String author = this.docToMeta.get(report).names.get(0);
				
				if (amo1.authorTopicToLinkProbs.containsKey(author) && amo1.authorTopicToLinkProbs.get(author).containsKey(t) && amo1.authorTopicToLinkProbs.get(author).containsKey(source)) {
					linkScore += (amo1.docToTopicProbabilities.get(report)[t]* amo1.authorTopicToLinkProbs.get(author).get(t).get(source));
				}
			} else {
				
				double total = 0;
				int numAuthorsFound = 0;
				for (String author : this.docToMeta.get(report).names) {
					//System.out.println("looking for " + author + " but our map is of " + amo1.authorTopicToLinkProbs.keySet());
					if (amo1.authorTopicToLinkProbs.containsKey(author) && amo1.authorTopicToLinkProbs.get(author).containsKey(t) && amo1.authorTopicToLinkProbs.get(author).get(t).containsKey(source)) {
						total += (amo1.docToTopicProbabilities.get(report)[t]* amo1.authorTopicToLinkProbs.get(author).get(t).get(source));
						numAuthorsFound++;
					}
				}
				
				if (numAuthorsFound > 0) {
					
					//if (t==0) { System.out.println("num authors foudn: "+ numAuthorsFound);}
					linkScore += (total / (double)numAuthorsFound);
				}
			}
		}
		return linkScore;
	}

	public double getAuthorLinkLDA2Score(String report, String source) {
		//System.out.println("getting lda score for " + report + " -> " + source);

		double total = 0;
		int aNum = 0;
		for (String author : this.docToMeta.get(report).names) {
			
			if (firstAuthor && aNum > 0) {
				break;
			}
			double linkScore = 0.0;
			for (int t=0; t<this.numTopics; t++) {
				
				//System.out.println("source: " + source + " report: " + report + " l|t: " + lmo.topicToLinkProbabilities.get(t).size());
				double topicProb = 1.0 / (double)this.numTopics;
				if (amo2.authorToTopicProbabilities.containsKey(author)) {
					//System.err.println("we don't have topic info for report:" + author);
					topicProb = amo2.authorToTopicProbabilities.get(author)[t];
				}

				if (amo2.topicToLinkProbabilities.get(t).containsKey(source)) {
					linkScore += (topicProb*(0.000001 + amo2.topicToLinkProbabilities.get(t).get(source)));
				} else {
					//System.out.println("we dont have source " + source);
					linkScore += (topicProb*(0.000001));
				}
			}
			total += linkScore;
			aNum++;
		}
		if (firstAuthor) {
			return total;
		}
		return total / (double)this.docToMeta.get(report).names.size();
		//return bayesianScore; //*0.5 + bayesianScore*0.5;
	}
	
	public double getAuthorLinkLDA3Score(String report, String source) {
		//System.out.println("getting lda score for " + report + " -> " + source);

		double total = 0;
		int aNum = 0;
		for (String author : this.docToMeta.get(report).names) {
			
			if (firstAuthor && aNum > 0) {
				break;
			}
			double linkScore = 0.0;
			for (int t=0; t<this.numTopics; t++) {
				
				//System.out.println("source: " + source + " report: " + report + " l|t: " + lmo.topicToLinkProbabilities.get(t).size());
				double topicProb = 1.0 / (double)this.numTopics;
				if (amo2.authorToTopicProbabilities.containsKey(author)) {
					//System.err.println("we don't have topic info for report:" + author);
					topicProb = amo2.authorToTopicProbabilities.get(author)[t];
				}

				if (amo2.topicToLinkProbabilities.get(t).containsKey(source)) {
					linkScore += (topicProb*(0.000001 + amo2.topicToLinkProbabilities.get(t).get(source)));
				} else {
					//System.out.println("we dont have source " + source);
					linkScore += (topicProb*(0.000001));
				}
			}
			total += linkScore;
			aNum++;
		}
		if (firstAuthor) {
			return total;
		}
		return total / (double)this.docToMeta.get(report).names.size();
		//return bayesianScore; //*0.5 + bayesianScore*0.5;
	}
	
	public double getLinkLDAScore(String report, String source) {
		//System.out.println("getting lda score for " + report + " -> " + source);
		double linkScore = 0.0;
		
		
		for (int t=0; t<this.numTopics; t++) {
			
			if (worstTopics.contains(t)) {
				//continue;
			}
			//System.out.println("source: " + source + " report: " + report + " l|t: " + lmo.topicToLinkProbabilities.get(t).size());
			if (!lmo.docToTopicProbabilities.containsKey(report)) {
				System.err.println("we don't have topic info for report:" + report);
			}
			/*
			if (!lmo.topicToLinkProbabilities.get(t).containsKey(source)) {
				System.err.println("we don't have link prof for source: " + source);
			}
			*/
			if (lmo.topicToLinkProbabilities.get(t).containsKey(source)) {
				if (report.equals("P07-1080")) {
					System.out.println(source + " | p07-1080 = " + lmo.topicToLinkProbabilities.get(t).get(source));
				}
				linkScore += (lmo.docToTopicProbabilities.get(report)[t]*(0.00001 + lmo.topicToLinkProbabilities.get(t).get(source)));
			} else {
				System.out.println("we dont have source " + source);
				linkScore += (lmo.docToTopicProbabilities.get(report)[t]*(0.00001));
			}
		}
		
		
		double bayesianScore = 0.0;
		for (int t=0; t<this.numTopics; t++) {
			
			if (worstTopics.contains(t)) {
				//continue;
			}
			double prob_source = 0.001; //0.0; //5.0; // TODO: fux with this
			if (this.citedCounts.containsKey(source)) {
				prob_source += (double)this.citedCounts.get(source);  //1; // uniform
			}
			prob_source /= (double)this.totalCitations;
			double prob_topic_given_source = lmo.docToTopicProbabilities.get(source)[t];
			double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];

			double prob_topic_given_report = lmo.docToTopicProbabilities.get(report)[t];
			
			if (bayesScoring) {
				bayesianScore += (prob_source_given_topic * prob_topic_given_report);
			} else {
				bayesianScore += (prob_topic_given_source * prob_topic_given_report);
			}
		}
		return linkScore; //bayesianScore;
		//return bayesianScore; //*0.5 + bayesianScore*0.5;
	}
	
	public double getBayesianScore(String report, String source) {
		double score = 0.0;

		int sourceYear = 0;
		// sourceYear = Integer.parseInt(docToMeta.get(candidateDoc).year);

		for (int t=0; t<this.numTopics; t++) {
			
			if (worstTopics.contains(t)) {
				//continue;
			}
			double prob_source = 0.001; //0.0; //5.0; // TODO: fux with this
			if (this.citedCounts.containsKey(source)) {
				prob_source += (double)this.citedCounts.get(source);  //1; // uniform
			}
			prob_source /= (double)this.totalCitations;
			double prob_topic_given_source = tmo.docToTopicProbabilities.get(source)[t];
			double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];

			double prob_topic_given_report = tmo.docToTopicProbabilities.get(report)[t];
			
			if (bayesScoring) {
				score += (prob_source_given_topic * prob_topic_given_report);
			} else {
				score += (prob_topic_given_source * prob_topic_given_report);
			}
		}
		return score;
	}
	
	// prints recall/precision and false positive vs true positive graphs averaged over all reports
	public void printAllGraphs(String outputDir, String corpus, String method) throws IOException {
		// used for limiting the output of the global way of displaying results;
		// displaying all of them would yield over 1 million rows
		int approxNumRows = 10000; 
		
		Map<Integer, List<Double>> recalls = new HashMap<Integer, List<Double>>();
		Map<Integer, List<Double>> precisions = new HashMap<Integer, List<Double>>();
		Map<Integer, List<Double>> falsePositives = new HashMap<Integer, List<Double>>();
		for (String report : reportToRankedSources.keySet()) {
		
			List<String> rankedSources = reportToRankedSources.get(report);
			
			int totalPositiveToFind = reportToTestingSources.get(report).size();
			int totalNegativeToFind = rankedSources.size() - totalPositiveToFind;
			
			int numReturned = 0;
			int numPositiveFound = 0;
			int numNegativeFound = 0;
			
			for (String source : rankedSources) {
				
				numReturned++;
				if (reportToTestingSources.get(report).contains(source)) {
					numPositiveFound++;
				} else {
					numNegativeFound++;
				}
				
				double recall = (double)numPositiveFound / (double)totalPositiveToFind;
				double precision = (double)numPositiveFound / (double)numReturned;
				double falsePos = (double)numNegativeFound / (double)totalNegativeToFind;

				// updates recall
				List<Double> tmp = new ArrayList<Double>();
				if (recalls.containsKey(numReturned)) {
					tmp = recalls.get(numReturned);
				}
				tmp.add(recall);
				recalls.put(numReturned, tmp);
				
				// updates precision
				tmp = new ArrayList<Double>();
				if (precisions.containsKey(numReturned)) {
					tmp = precisions.get(numReturned);
				}
				tmp.add(precision);
				precisions.put(numReturned, tmp);
				
				// updates falsepos
				tmp = new ArrayList<Double>();
				if (falsePositives.containsKey(numReturned)) {
					tmp = falsePositives.get(numReturned);
				}
				tmp.add(falsePos);
				falsePositives.put(numReturned, tmp);
			}	
		} // end of going through all reports
		
		BufferedWriter avgG1 = new BufferedWriter(new FileWriter(outputDir + "avgG1-" + corpus + "-" + method + ".csv"));
		BufferedWriter avgG2 = new BufferedWriter(new FileWriter(outputDir + "avgG2-" + corpus + "-" + method + ".csv"));
		BufferedWriter avgG3 = new BufferedWriter(new FileWriter(outputDir + "avgG3-" + corpus + "-" + method + "_" + alpha + ".csv"));
	    avgG1.write("recall,precision\n");
	    avgG2.write("false_pos,true_pos\n");
	    avgG3.write("#returned, recall %\n");
	   
		SortedSet<Integer> keys = new TreeSet<Integer>(recalls.keySet());
		for (Integer key : keys) { 
			double recallAvg = 0;
			for (double r : recalls.get(key)) {
				recallAvg += r;
			}
			recallAvg /= recalls.get(key).size();
			
			double precAvg = 0;
			for (double p : precisions.get(key)) {
				precAvg += p;
			}
			precAvg /= precisions.get(key).size();
			
			double falsePosAvg = 0;
			for (double f : falsePositives.get(key)) {
				falsePosAvg += f;
			}
			falsePosAvg /= falsePositives.get(key).size();
			
			avgG1.write(recallAvg + "," + precAvg + "\n");
			avgG2.write(falsePosAvg + "," + recallAvg + "\n");
			avgG3.write(key + "," + recallAvg + "\n");
		}
		avgG1.close();
		avgG2.close();
		avgG3.close();
		recalls = new HashMap<Integer, List<Double>>();
		precisions = new HashMap<Integer, List<Double>>();
		falsePositives = new HashMap<Integer, List<Double>>();
		// END OF GOING THROUGH MY EVALUATION WAY (AVG PERFORMANCE PER REPORT)
		
		
		// PMTLM's way of evaluating on a global ranking scale
		/*
		double rate = (double)approxNumRows / (double)this.globalSourceProbs.keySet().size();
		
		BufferedWriter globalG1 = new BufferedWriter(new FileWriter(outputDir + "globalG1-" + corpus + "-" + method + ".csv"));
		BufferedWriter globalG2 = new BufferedWriter(new FileWriter(outputDir + "globalG2-" + corpus + "-" + method + ".csv"));
	    globalG1.write("recall,precision\n");
	    globalG2.write("false_pos,true_pos\n");
	    
		int totalPositiveToFind = 0; //reportToSources.get(report).size();
		int totalNegativeToFind = 0; //rankedSources.size() - totalPositiveToFind;
		for (String report : reportToRankedSources.keySet()) {
			totalPositiveToFind += reportToTestingSources.get(report).size();
			totalNegativeToFind += (reportToRankedSources.get(report).size() - reportToTestingSources.get(report).size());
		}
		
		System.out.println("totalpostofind: " + totalPositiveToFind);
		System.out.println("totalnegtofind: " + totalNegativeToFind);
		
		int numReturned = 0;
		int numPositiveFound = 0;
		int numNegativeFound = 0;
		
		Iterator it = sortByValueDescending(globalSourceProbs).keySet().iterator();
		while (it.hasNext()) {
			
			numReturned++;
			String key = (String)it.next();
			String d1 = key.split("_")[0];
			String d2 = key.split("_")[1];
			
			if (reportToTestingSources.get(d1).contains(d2)) {
				numPositiveFound++;
			} else {
				numNegativeFound++;
			}
			
			double recall = (double)numPositiveFound / (double)totalPositiveToFind;
			double precision = (double)numPositiveFound / (double)numReturned;
			double falsePos = (double)numNegativeFound / (double)totalNegativeToFind;
			
			Random rand = new Random();
			if (rand.nextDouble() < rate) {
				globalG1.write(recall + "," + precision + "\n");
				globalG2.write(falsePos + "," + recall + "\n");
			}
		}
		
		System.out.println("found ps: " + numPositiveFound);
		System.out.println("found neg: " + numNegativeFound);
		globalG1.close();
		globalG2.close();
	*/
	} 
	
	public void getCitedStats(String outputDir, int numBins) {
		// TODO Auto-generated method stub
		
		
		// out of curiousity, keep track of how many 
		double[] globalTotalVanillaCounts = new double[numBins];
		double[] globalTotalPrevCitedCounts = new double[numBins];
		
		// accumulates totals, which we'll later divide by the # of reports
		double[] globalAvgVanillaCounts = new double[numBins];
		double[] globalAvgPrevCitedCounts = new double[numBins];
		
		for (String report : reportToRankedSources.keySet()) {
			
			double[] reportTotalVanillaCounts = new double[numBins];
			double[] reportTotalPrevCitedCounts = new double[numBins];
			double[] reportCitedVanillaCounts = new double[numBins];
			double[] reportCitedPrevCitedCounts = new double[numBins];
			MetaDoc mdReport = docToMeta.get(report);
			int reportYear = Integer.parseInt(mdReport.year);
			
			int numSources = reportToRankedSources.get(report).size();
			int avgBinSize = (int) Math.floor((double)numSources/(double)numBins);
			System.out.println("avg binsize: " + avgBinSize);
			int sourceNum=1;
			for (String source : reportToRankedSources.get(report)) {
				int binNum = Math.min((int) Math.floor((double)sourceNum / (double)avgBinSize), numBins-1);
				MetaDoc mdSource = docToMeta.get(source);
				
				
				boolean isCited = reportToTestingSources.get(report).contains(source);
				// checks if any of the authors has cited this paper before
				boolean prevCitedThisSource = false;
				for (String author : docToMeta.get(report).names) {
					for (String authReport : authorToReports.get(author)) {
						MetaDoc md2 = docToMeta.get(authReport);
						int report2Year = Integer.parseInt(md2.year);
						if (report2Year <= reportYear && !report.equals(authReport)) {
							
							// checks if the author has cited this paper before (can be in training or test, since it's a diff report)
							if ((this.reportToTrainingSources.containsKey(authReport) && reportToTrainingSources.get(authReport).contains(source))
									/* || (this.reportToTestingSources.containsKey(authReport) && reportToTestingSources.get(authReport).contains(source))*/) {
								prevCitedThisSource = true;
								//System.out.println("; report: " + report + "(" + reportYear + ") has author: " + author + " whom prev cited " + source + " in his report: " + authReport + " which was written in " + report2Year);
							}
						}
					}
				}
				
				reportTotalVanillaCounts[binNum]++;
				if (isCited) {
					reportCitedVanillaCounts[binNum]++;
				}
				if (prevCitedThisSource) {
					reportTotalPrevCitedCounts[binNum]++;
					
					if (isCited) {
						reportCitedPrevCitedCounts[binNum]++;
					}
				}
				
				sourceNum++;
			} // end of going through all sources
			
			
			// let's calculate the probs!
			for (int i=0; i<numBins; i++) {
				double pVanilla = (double)reportCitedVanillaCounts[i] / (0.0001 + (double)reportTotalVanillaCounts[i]);
				double pPrevCited = (double)reportCitedPrevCitedCounts[i] / (0.0001 + (double)reportTotalPrevCitedCounts[i]);
				System.out.print(reportTotalPrevCitedCounts[i] + ",");
				globalAvgVanillaCounts[i] += pVanilla;
				globalAvgPrevCitedCounts[i] += pPrevCited;
				
				globalTotalVanillaCounts[i] += reportTotalVanillaCounts[i];
				globalTotalPrevCitedCounts[i] += reportTotalPrevCitedCounts[i];
			}
			System.out.println("");
		} // end of going through all reports
		
		// let's average
		int n = reportToRankedSources.keySet().size();
		for (int i=0; i<numBins; i++) {
			globalAvgVanillaCounts[i] /= n;
			globalAvgPrevCitedCounts[i] /= n;
		
			globalTotalVanillaCounts[i] /= n;
			globalTotalPrevCitedCounts[i] /= n;
		}
		
		System.out.println("global avg van probs");
		for (int i=0; i<numBins; i++) {
			System.out.print(globalAvgVanillaCounts[i] + ",");
		}
		System.out.println("");
		
		System.out.println("global avg cited probs");
		for (int i=0; i<numBins; i++) {
			System.out.print(globalAvgPrevCitedCounts[i] + ",");
		}
		System.out.println("");
		
		System.out.println("global van count");
		for (int i=0; i<numBins; i++) {
			System.out.print(globalTotalVanillaCounts[i] + ",");
		}
		System.out.println("");
		
		System.out.println("global cited counts");
		for (int i=0; i<numBins; i++) {
			System.out.print(globalTotalPrevCitedCounts[i] + ",");
		}
		System.out.println("");
	}	
	
	
	// returns (and optionally prints) the recall results for ALL reports
	public List<Double> getRecallResults(String outputDir, boolean writeFile) throws IOException {
		List<Double> recallTotals = new ArrayList<Double>();
		List<Integer> numItems = new ArrayList<Integer>();
		List<Double> ret = new ArrayList<Double>();
		
		System.out.println("*** evaluating " + reportToRankedSources.keySet().size() + " reports!");
		
		for (String doc : reportToRankedSources.keySet()) {
			List<Double> tmp = getRecallResults(doc);
			for (int i=0; i<tmp.size(); i++) {
				// expands the list to a new size
				if (recallTotals.size() <= i) {
					recallTotals.add(tmp.get(i));
					numItems.add(1);
					
				// updates a value within our recall list
				} else {
					double curVal = recallTotals.get(i);
					recallTotals.remove(i);
					recallTotals.add(i, curVal + tmp.get(i));
					
					int numVals = numItems.get(i);
					numItems.remove(i);
					numItems.add(i, ++numVals);
				}
			}
		}
		
		for (int i=0; i<recallTotals.size(); i++) {
			ret.add(recallTotals.get(i) / (double)numItems.get(i));
		}
		return ret;
	}
	
	private List<Double> getRecallResults(String report) {
		int numGoldenSources = reportToTestingSources.get(report).size();
		int numCorrect = 0;
		List<Double> ret = new ArrayList<Double>();
		for (String source : reportToRankedSources.get(report)) {
			if (reportToTestingSources.get(report).contains(source)) {
				numCorrect++;
			}
			ret.add((double)numCorrect / (double)numGoldenSources);
		}
		return ret;
	}

	// prints topic-link info for LinkLDA model
	public void printLinkLDASpecifics(String output) throws IOException {
		
		System.out.println("# metadocs: " + docToMeta.keySet().size());
		
		BufferedWriter bout = new BufferedWriter(new FileWriter(output));
		for (int t=0; t<this.numTopics; t++) {
			bout.write("topic " + t + "\n--------------------------\n");
			
			Map<String, Double> wordProbs = lmo.topicToWordProbabilities.get(t);
			int i=0;
			Iterator it = this.sortByValueDescending(wordProbs).keySet().iterator();
			while (it.hasNext() && i < 20) {
				String word = (String)it.next();
				bout.write(word + ",");
				i++;
			}
			bout.write("\n");
			
			Map<String, Double> linkProbs = lmo.topicToLinkProbabilities.get(t);
			it = this.sortByValueDescending(linkProbs).keySet().iterator();
			i=0;
			while (it.hasNext() && i < 50) {
				String link = (String)it.next();
				double prob = linkProbs.get(link);
				String title = "";
				if (docToMeta.containsKey(link)) {
					title = docToMeta.get(link).title;
				}
				System.out.println("link: " + link + "; title: " + title);
				bout.write("\t" + link + " (" + title + ") = " + prob + "\n");
				i++;
			}
		}
		bout.close();
	}
	

	/*
	// NOTE: this method is now DEFUNCT and is only for LDA because it prints topic info based on the ldaObject
	// returns (and optionally prints) the recall results for the passed-in file
	public List<Double> writeTopicModelResults(String report, String outputDir, boolean writeFile) throws IOException {
		List<Double> recall = new ArrayList<Double>();
		int reportYear = Integer.parseInt(docToMeta.get(report).year);
		
		List<Integer> positions = new ArrayList<Integer>();
		for (int i : this.numSourcesCutoffs) {
			positions.add(i);
		}
		
		// for the passed-in report, determine P(S|R) for all sources which aren't the report
		Map<String, Double> sourceProbs = new HashMap<String, Double>();
		for (String source : sourceNames) {
			if (source.equals(report)) {
				continue;
			}

			// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
			double score = 0.0;
			
			int sourceYear = Integer.parseInt(docToMeta.get(source).year);
			
			if (sourceYear <= reportYear) {
				for (int t=0; t<this.numTopics; t++) {
					
					double prob_source = (double)this.citedCounts.get(source) / (double)this.totalCitations; //1; // uniform
					double prob_topic_given_source = this.tmo.docToTopicProbabilities.get(source)[t];
					double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];
					
					double prob_topic_given_report = this.tmo.docToTopicProbabilities.get(report)[t];
					score += (prob_source_given_topic * prob_topic_given_report);
				}
			}
			sourceProbs.put(source, score);
		}
		
		// prints topic info for the report
		Set<String> targetSources = this.reportToSources.get(report);
		BufferedWriter bout = null;
		if (writeFile) {
			bout = new BufferedWriter(new FileWriter(outputDir + report + ".txt"));
			bout.write("report:" + docToMeta.get(report).title + " (" + docToMeta.get(report).year + ")\n");
			bout.write("total valid sources:" + targetSources.size() + "\n\n");
			bout.write("pos cited? title (year)\n");
			
			// print the topic proportions: topic # (prob)
			DecimalFormat df = new DecimalFormat("##.##");
			Map<Integer, Double> tmp = new HashMap<Integer, Double>();
			for (int t=0; t<this.numTopics; t++) {
				tmp.put(t, this.tmo.docToTopicProbabilities.get(report)[t]);
			}
			Iterator it3 = sortByValueDescending(tmp).keySet().iterator();
			int j=0;
			bout.write("\t");
			while (it3.hasNext() && j < 10) {
				int topicNum = (Integer)it3.next();
				double prob = tmp.get(topicNum);
				bout.write(topicNum + " (" + df.format(prob) + ") ");
				j++;
			}
			bout.write("\n");
		}
		
		// sort sourceProbs in decreasing order
		Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
		int i=1;
		int numCorrect = 0;
		DecimalFormat df = new DecimalFormat("##.##");
		while (it.hasNext()) {
			String source = (String)it.next();
			
			if (bout != null) {
				MetaDoc md = docToMeta.get(source);
				if (targetSources.contains(source)) {
					numCorrect++;

					bout.write(i + " * " + docToMeta.get(source).title + " (" + md.year + ") " + this.citedCounts.get(source) + "\n");
				} else {
					bout.write(i + "   " + docToMeta.get(source).title + " (" + md.year + ") " + this.citedCounts.get(source) + "\n");
				}
				
				// print the topic proportions: topic # (prob)
				Map<Integer, Double> tmp = new HashMap<Integer, Double>();
				for (int t=0; t<this.numTopics; t++) {
					tmp.put(t, this.tmo.docToTopicProbabilities.get(source)[t]);
				}
				Iterator it3 = sortByValueDescending(tmp).keySet().iterator();
				int j=0;
				bout.write("\t");
				while (it3.hasNext() && j < 10) {
					int topicNum = (Integer)it3.next();
					double prob = tmp.get(topicNum);
					bout.write(topicNum + " (" + df.format(prob) + ") ");
					j++;
				}
				bout.write("\n");
			} else {
				if (targetSources.contains(source)) {
					numCorrect++;
				}
			}
			if (positions.size() > 0 && positions.get(0) == i) {
				recall.add((double)numCorrect / (double)targetSources.size());
				positions.remove(0);
			}
			

			i++;
		}
		if (bout != null) { bout.close(); }
		return recall;
	}

	// returns the list of average # of sentences we must return per report
	// (1 element per report)
	public List<Integer> writeSentencePredictions(String outputDir, boolean writeFile) throws IOException {
		List<Integer> ret = new ArrayList<Integer>();
		
		return ret;
	}
	
	// returns the List of # of sentences we must return in order to find the sentence that corresponds with the report's sources
	// (1 element per source)
	// optionally, we print the analysis to the passed-in file
	public List<Integer> writeSentencePredictions(String report, String outputDir, boolean writeFile) throws IOException {
		List<Integer> ret = new ArrayList<Integer>();
		
		// loads the ACLDocument
		ACLDocument d = loadACLDocument(this.docsPath + report + ".txt");
		System.out.println(report + " has " + d.citedListPerSentence.size() + " sentences");
		for (String source : d.citationToMatched.keySet()) {
			ret.add(writeSentencePredictions(d, report, source, outputDir, writeFile));
		}
		return ret;
	}


	// returns the # of sentences we must return in order to find the sentence that corresponds with the passed-in source
	// optionally, we print the analysis to the passed-in file
	public int writeSentencePredictions(ACLDocument d, String report, String source, String outputDir, boolean writeFile) throws IOException {
		int ret = 9999;
		Map<Integer, Double> sentenceProbs = new HashMap<Integer, Double>();
		for (int i=0; i<d.citedListPerSentence.size(); i++) {
			// argmax topic way
			double lowestProb = 999999;
			for (int t=0; t<this.numTopics; t++) {
				String curSentence = d.cleanedContentPerSentence.get(i);
				StringTokenizer st = new StringTokenizer(curSentence);
				int numWords = st.countTokens();
				double sentenceProb = 0;
				while (st.hasMoreTokens()) {
					String word = st.nextToken();
					
					double wordProb = 0.0001;
					double prob_topic_given_source = tmo.docToTopicProbabilities.get(source)[t];
					double prob_word_given_topic = 0;
					if (tmo.topicToWordProbabilities.get(t).containsKey(word)) {
						prob_word_given_topic = tmo.topicToWordProbabilities.get(t).get(word);
					}
					
					wordProb += prob_word_given_topic * prob_topic_given_source;
					sentenceProb += -1.0 * Math.log(wordProb);
				}
				sentenceProb = sentenceProb / (double)numWords;
				if (sentenceProb < lowestProb) {
					lowestProb = sentenceProb;
				}
			}
			sentenceProbs.put(i, lowestProb);
			// all topics way
			
//			String curSentence = d.cleanedContentPerSentence.get(i);
//			StringTokenizer st = new StringTokenizer(curSentence);
//			double sentenceProb = 0;
//			int numWords = st.countTokens();
//			while (st.hasMoreTokens()) {
//				String word = st.nextToken();
//				double wordProb = 0.0001;
//				for (int t=0; t<this.numTopics; t++) {
//					double prob_topic_given_source = lda.docToTopicProbabilities.get(source)[t];
//					double prob_word_given_topic = 0;
//					if (lda.topicToWordProbabilities.get(t).containsKey(word)) {
//						prob_word_given_topic = lda.topicToWordProbabilities.get(t).get(word);
//					}
//					
//					wordProb += prob_word_given_topic * prob_topic_given_source;
//				}
//				sentenceProb += -1.0 * Math.log(wordProb);
//			}
//			sentenceProbs.put(i, sentenceProb / (double)numWords);
			
		}

		// sort in increasing order?
		BufferedWriter bout = null;
		if (writeFile) {
			bout = new BufferedWriter(new FileWriter(outputDir + report + "_" + source + ".txt"));
			bout.write("sentence #   prob   sentence\n");
		}
		Iterator it = sortByValueAscending(sentenceProbs).keySet().iterator();
		int i=0;
		while (it.hasNext()) {
			int sentenceNum = (Integer)it.next();
			double prob = sentenceProbs.get(sentenceNum);
			
			// we found the sentence
			if (d.citedListPerSentence.get(sentenceNum).contains(source)) {
				if (i<ret) {
					ret = i;
				}
				if (writeFile) {
					bout.write(sentenceNum + "* " + prob + " " + d.rawContentPerSentence.get(sentenceNum) + "\n");
				}
			} else {
				if (writeFile) {
					bout.write(sentenceNum + " " + prob + " " + d.rawContentPerSentence.get(sentenceNum) + "\n");
				}
			}
			i++;
		}
		
		// now writes the output in regular, doc form
		if (writeFile) {
			bout.write("\n\n\n----------------------------------\n\n\n");
			for (i=0; i<d.citedListPerSentence.size(); i++) {
				double prob = sentenceProbs.get(i);
				if (d.citedListPerSentence.get(i).contains(source)) {
					bout.write(i + "* " + prob + " " + d.rawContentPerSentence.get(i) + "\n");
				} else {
					bout.write(i + " " + prob + " " + d.rawContentPerSentence.get(i) + "\n");
				}
			}
		}
		if (writeFile) {
			bout.close();
		}
		return ret;
	}
	
	private ACLDocument loadACLDocument(String report) throws IOException {
		ACLDocument d = null;
		
		BufferedReader bin = new BufferedReader(new FileReader(report));
		String curLine = "";
		
		List<Set<String>> citedListPerSentence = new ArrayList<Set<String>>();
		List<String> rawContentPerSentence = new ArrayList<String>();
		List<String> cleanedContentPerSentence = new ArrayList<String>();
		
		// we keep track of this because not all of the ones 
		// in reportToSources may have been found (since we accept 90% in the ACL data creation)
		Map<String, Boolean> allSources = new HashMap<String, Boolean>(); 
		
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine, "\t");
			//System.out.println(st.countTokens() + ":" + curLine);
			String cites = st.nextToken();
			Set<String> curCites = new HashSet<String>();
			if (!cites.equals("[]")) {
				String inside = cites.substring(cites.indexOf("[")+1).replace("]", "");
				StringTokenizer st2 = new StringTokenizer(inside, ", ");
				while (st2.hasMoreTokens()) {
					String s = (String)st2.nextToken();
					curCites.add(s);
					allSources.put(s, true);
				}
			}
			
			// skip section stuff
			st.nextToken();
			st.nextToken();
			String rawContent = st.nextToken();
			String cleanedContent = st.nextToken();
			
			// updates our per-line variables 
			citedListPerSentence.add(curCites);
			rawContentPerSentence.add(rawContent);
			cleanedContentPerSentence.add(cleanedContent);
		}
		d = new ACLDocument(citedListPerSentence, rawContentPerSentence, cleanedContentPerSentence, allSources);
		return d;
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
	
	@SuppressWarnings("unchecked")
	static Map sortByValueAscending(Map map) {
	     List list = new LinkedList(map.entrySet());
	     Collections.sort(list, new Comparator() {
	          public int compare(Object o1, Object o2) {
	               return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue());
	          }
	     });

	    Map result = new LinkedHashMap();
	    for (Iterator it = list.iterator(); it.hasNext();) {
	        Map.Entry entry = (Map.Entry)it.next();
	        result.put(entry.getKey(), entry.getValue());
	    }
	    return result;
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


	

}

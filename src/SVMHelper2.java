import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
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

import com.sun.corba.se.impl.javax.rmi.CORBA.Util;

// the difference between this class and '#1' is that this is for
// when we're allowing our system to have a training and test set, not just for SVM.
// and, it's setup to work with PMTLM, too
public class SVMHelper2 {

	// params
	public static String corpus = "acl_20000";
	public static String dataDir = "/Users/christanner/research/projects/CitationFinder/eval/";
	//public static String dataDir = "/users/ctanner/data/ctanner/";
	
	public static String textSuffix = ""; // set to -dc if we're to use the -dc model
	public static int numTopics = 50;
	public static boolean evaluateSVM = true;
	
	// input
	public static String metaDocsFile = dataDir + "acl-metadata.txt";

	public static String trainingGoldFile = dataDir + corpus + ".training";
	public static String testingGoldFile = dataDir + corpus +".testing";
	
	public static String topicModelScores = dataDir + "scores_lda_" + corpus + ".txt"; 
	//public static String pmtlmScores = "/Users/christanner/research/projects/CitationFinder/eval/scores_pmtlm_" + corpus + ".txt";
	
	public static String topicModelObject = dataDir + "lda_" + corpus + "_2000i.ser";
	
	// output 
	public static String trainingSVMFile = dataDir + "svm_" + corpus + "_training_features_lda_prev.txt";
	public static String testingSVMFile = dataDir + "svm_" + corpus + "_testing_features_lda_prev.txt";
	public static String testingSVMTruth = dataDir + "svm_" + corpus + "_testing_truth_lda_prev.txt";
	
	public static String tmpOut = dataDir + "tmp.txt";
	
	public static String outputDir = dataDir;
	
	// files for evaluation
	//public static String evaluateSVMTruth = "/Users/christanner/research/projects/CitationFinder/eval/svm_" + corpus + "_testing_truth.txt";
	public static String svmPredictions = dataDir + "svm_" + corpus + "_testing_lda_prev.predictions";
	public static String stoplist = dataDir + "stopwords.txt";
	
	// global vars
	public static Map<String, MetaDoc> docIDToMeta = new HashMap<String, MetaDoc>();
	public static Set<String> allDocuments = new HashSet<String>();
	public static Set<String> candidateSources = new HashSet<String>(); // NOTE: this has the bipartite assumption -- our candidates sources are exactly ones that we've seen in training or test
	public static Set<String> testingDocuments = new HashSet<String>();
	
	public static Map<String, Set<String>> trainingReportToSources = new HashMap<String, Set<String>>();
	public static Map<String, Set<String>> testingGoldenReportToSources = new HashMap<String, Set<String>>();
	//public static Map<Short, Map<Short, Float>> topicReportToSources = new HashMap<Short, Map<Short, Float>>();
	public static Map<String, Map<String, Double>> pmtlmReportToSources = new HashMap<String, Map<String, Double>>();
	public static Map<String, Set<String>> authorToReports = new HashMap<String, Set<String>>();
	
	public static Double[] topicSums = null;
	public static Map<String, Integer> citedCounts = new HashMap<String, Integer>();
	public static int totalCitations = 0;
	public static int posToNegRatio = 5;
	public static int highestCount = 0;
	//public static LinkLDAModelObject tmo = null;
	public static TopicModelObject tmo = null;
	public static PMTLMModelObject pmo = null;

	//public static Map<String, Short> docNameToID = new HashMap<String, Short>();
	//public static Map<Short, String> docIDToName = new HashMap<Short, String>();
	
	public static Set<String> stopwords = new HashSet<String>();
	
	public static void main(String[] args) throws IOException {
		
		stopwords = loadList(stoplist);
		loadTopicModelObject(topicModelObject);
		loadTrainingAndTestingCites(trainingGoldFile, testingGoldFile);
		System.out.println("# testing reports: " + testingGoldenReportToSources.keySet().size());
		loadMetaDocs(metaDocsFile);
		
		
		//pmtlmReportToSources = loadScores(pmtlmScores);
		
		if (!evaluateSVM) {
			//topicReportToSources = loadScores(topicModelScores);
			createSVMTrainingAndTest(trainingSVMFile, testingSVMFile, testingSVMTruth);
		} else {
			//topicReportToSources = loadScores(topicModelScores);
			evaluateSVMPredictions2(testingSVMTruth, svmPredictions);			
		}
	}

	private static void evaluateSVMPredictions2(String truth, String predictions) throws IOException {
		
		
		// this is just eugene's idea of trying to test why SVM's results for the 1st ~50 are worse;
		// we will go through training and calculate P(S|Report at index i) and P(S|Report at index i and prevCited)
		numTopics = tmo.topicToWordProbabilities.keySet().size();
		// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
		System.out.println("* preprocessing P(S|Z)...");
		topicSums = new Double[numTopics];
		for (int t=0; t<numTopics; t++) {
			double totalSum = 0.0;
			
			for (String candidateDoc : candidateSources /*sources*/) {
				
				double prob_source = 0.01;
				if (citedCounts.containsKey(candidateDoc)) {
					prob_source += (double)citedCounts.get(candidateDoc);
				}
				prob_source /= (double)totalCitations; //1; // uniform
				double prob_topic_given_source = tmo.docToTopicProbabilities.get(candidateDoc)[t];
				totalSum += (prob_topic_given_source * prob_source);
			}
			topicSums[t] = totalSum;
			//System.out.println("topicSums[" + t + "]:" + topicSums[t]);
		}
		System.out.println("done!");
		System.out.println("*** topicSums[0] = " + topicSums[0]);
		System.out.println("*** candidate sources size: " + candidateSources.size());
		
		BufferedWriter tmp1 = new BufferedWriter(new FileWriter(outputDir + "tmp_ours.txt"));
		BufferedWriter tmp2 = new BufferedWriter(new FileWriter(outputDir + "tmp_lda.txt"));
		BufferedWriter tmp3 = new BufferedWriter(new FileWriter(outputDir + "tmp_scores.csv"));
		
		BufferedReader binTruth = new BufferedReader(new FileReader(truth));
		BufferedReader binPredictions = new BufferedReader(new FileReader(predictions));
		binPredictions.readLine(); // skips past the header of labels
		String curTruth = "";
		String curPrediction = "";
		String pastReport = "";
		
		boolean isFirstLine = true;
		Map<String, Double> sourceProbs = new HashMap<String, Double>();
		Map<Integer, List<Double>> recalls = new HashMap<Integer, List<Double>>();
		int reportNum = 0;
		while ((curTruth = binTruth.readLine())!=null) {
			curPrediction = binPredictions.readLine(); // reads the other file too, sync'd
			
			// reads the report and source
			StringTokenizer st = new StringTokenizer(curTruth);
			String report = (String)st.nextToken();
			String source = (String)st.nextToken();
			if (report == null || source == null) {
				System.out.println("found null: " + curTruth);
			}
			
			if (trainingReportToSources.containsKey(pastReport) && trainingReportToSources.get(pastReport).contains(source)) {
				continue;
			}
			
			// reads the probability
			st = new StringTokenizer(curPrediction);
			st.nextToken(); // skips past the label prediction (we care about the probabilities)
			double prob = Double.parseDouble(st.nextToken());
			
			//if (isFirstLine) {
			//	sourceProbs = getLDASourceProbs(report);
			//}
			
			// continuing from past report
			if (report.equals(pastReport) || isFirstLine) {
				sourceProbs.put(source, prob);
				isFirstLine = false;
			} else { // starts a new report
			//if (!report.equals(pastReport) && !isFirstLine) {
				// evaluates the past report, since we're now done with it
				//sourceProbs = getLDASourceProbs(pastReport); // only have this line if we want to test LDA's scores
				

				List<String> rankedList = rankTestingSources(pastReport, sourceProbs);
				// just used for trying to understand 1 particular report;
				if (pastReport.equals("P08-1023")) {
					Map<String, Double> ldaProbs = getLDASourceProbs(pastReport);
					List<String> ldarankedList = rankTestingSources(pastReport, ldaProbs);
					/* just displays sources in ABC order -- to ensure we were comparing LDA's sources and SVM's the same way.. same source set <> for eval
					SortedSet<String> keys = new TreeSet<String>(rankedList);
					tmp1.write("sources for " + pastReport + "\n");
					int i=0;
					for (String key : keys) {
						tmp1.write(key + "\n");
						tmp3.write(i++ + "," + ldaProbs.get(key) + "\n");
					}
					tmp3.close();
					sourceProbs = getLDASourceProbs(pastReport);
					*/
					//System.out.println("lda's score:" + sourceProbs.get("P06-1015"));
					//System.exit(1);
					tmp1.write("svmp_index, lda_index, * = is cited\n");
					tmp2.write("lda_index, svm_index, * = is cited\n");
					rankedList = rankTestingSources(pastReport, sourceProbs);
					int numFoundA=0;
					int numFoundB=0;
					for (int i=0; i<100; i++) {
						tmp1.write(i + "," + ldarankedList.indexOf(rankedList.get(i)) + ",");
						tmp2.write(i + "," + rankedList.indexOf(ldarankedList.get(i)) + ",");
						if (testingGoldenReportToSources.get(pastReport).contains(rankedList.get(i))) {
							tmp1.write("*\n");
							numFoundA++;
						} else {
							tmp1.write("\n");
						}
						if (testingGoldenReportToSources.get(pastReport).contains(ldarankedList.get(i))) {
							tmp2.write("*\n");
							numFoundB++;
						} else {
							tmp2.write("\n");
						}
					}
					tmp1.write("\nfound:" + numFoundA + "\n");
					tmp2.write("\nfound:" + numFoundB + "\n");
					/*
					SortedSet<String> keys = new TreeSet<String>(rankedList);
					tmp2.write("sources for " + pastReport + "\n");
					for (String key : keys) { 
						tmp2.write(key + "\n");
					}
					*/
					tmp1.close();
					tmp2.close();
				}
				
				int numPositiveFound = 0;
				int totalPositiveToFind = testingGoldenReportToSources.get(pastReport).size();
				for (int i=0; i<rankedList.size(); i++) {
					
					if (testingGoldenReportToSources.get(pastReport).contains(rankedList.get(i))) {
						numPositiveFound++;
					}
					double recall = (double)numPositiveFound / (double)totalPositiveToFind;
					
					// updates recall
					List<Double> tmp = new ArrayList<Double>();
					if (recalls.containsKey(i)) {
						tmp = recalls.get(i);
					}
					tmp.add(recall);
					recalls.put(i, tmp);
				}
				
				sourceProbs = new HashMap<String, Double>();
				sourceProbs.put(source, prob);
				//sourceProbs = getLDASourceProbs(report);
				
				reportNum++;
				if (reportNum % 100 == 0) {
					System.out.println("reportNum: " + reportNum);
				}
			}
			pastReport = report;
			isFirstLine = false;
		}
		// end of going through files; i think size should always be > 0
		if (sourceProbs.size() > 0) {
			sourceProbs = getLDASourceProbs(pastReport); // only have this line if we want to test LDA's scores
			// evaluates the past report, since we're now done with it
			List<String> rankedList = rankTestingSources(pastReport, sourceProbs);
			int numPositiveFound = 0;
			int totalPositiveToFind = testingGoldenReportToSources.get(pastReport).size();
			for (int i=0; i<rankedList.size(); i++) {
				if (testingGoldenReportToSources.get(pastReport).contains(rankedList.get(i))) {
					numPositiveFound++;
				}
				double recall = (double)numPositiveFound / (double)totalPositiveToFind;
				
				// updates recall
				List<Double> tmp = new ArrayList<Double>();
				if (recalls.containsKey(i)) {
					tmp = recalls.get(i);
				}
				tmp.add(recall);
				recalls.put(i, tmp);
			}
		}
		
		BufferedWriter avgG3 = new BufferedWriter(new FileWriter(outputDir + "avgG3-" + corpus +  "-svm.csv"));
	    avgG3.write("#returned, recall %\n");
		SortedSet<Integer> keys = new TreeSet<Integer>(recalls.keySet());
		for (Integer key : keys) { 
			double recallAvg = 0;
			for (double r : recalls.get(key)) {
				recallAvg += r;
			}
			recallAvg /= recalls.get(key).size();
			
			avgG3.write((key+1) + "," + recallAvg + "\n");
		}
		avgG3.close();
	}
	
	private static List<String> rankTestingSources(String pastReport, Map<String, Double> sourceProbs) {
		List<String> ret = new ArrayList<String>();
		Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
		while (it.hasNext()) {
			String s = (String)it.next();
			if (trainingReportToSources.containsKey(pastReport) && trainingReportToSources.get(pastReport).contains(s)) {
				continue;
			}
			ret.add(s);
		}
		return ret;
	}

	private static void evaluateSVMPredictions(String truth, String predictions) throws IOException {
		
		//Map<String, Double> globalSourceProbs = new HashMap<String, Double>();
		Map<Integer, String> lineNumToReportName = new HashMap<Integer, String>();
		Map<Integer, String> lineNumToSourceName = new HashMap<Integer, String>(); 
		
		Map<String, Map<Integer, Double>> reportToPredictions = new HashMap<String, Map<Integer, Double>>();
		//Map<String, Map<String, Double>> reportToSourcesScores = new HashMap<String, Map<String, Double>>();
		
		BufferedReader binTruth = new BufferedReader(new FileReader(truth));
		String curLine = "";
		int lineNum = 1;
		while ((curLine = binTruth.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String report = (String)st.nextToken();
			String source = (String)st.nextToken();
			
			if (report == null || source == null) {
				System.out.println("found null: " + curLine);
			}
			lineNumToReportName.put(lineNum, report);
			lineNumToSourceName.put(lineNum, source);
			lineNum++;	
		}
		binTruth.close();
		
		BufferedReader binPredictions = new BufferedReader(new FileReader(predictions));
		lineNum = 1;
		binPredictions.readLine(); // skips past the header of labels
		Set<String> comparedSources = new HashSet<String>();
		
		while ((curLine = binPredictions.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			st.nextToken(); // skips past the label prediction (we care about the probabilities)
			double prob = Double.parseDouble(st.nextToken());
			
			Map<Integer, Double> curPredictions = new HashMap<Integer, Double>();
			String report = lineNumToReportName.get(lineNum);
			String source = lineNumToSourceName.get(lineNum);
		
			if (report.equals("D13-1109")) {
				comparedSources.add(source);
			}
			
			if (reportToPredictions.containsKey(report)) {
				curPredictions = reportToPredictions.get(report);
			}
			curPredictions.put(lineNum, prob);
			reportToPredictions.put(report, curPredictions);
			lineNum++;
		} // end of reading through predictions file
		binPredictions.close();
		
		int approxNumRows = 2000;
		
		System.out.println("we have prediction scores for " + reportToPredictions.keySet().size() + " reports");
		System.out.println("testing saw : " + testingGoldenReportToSources.keySet().size() + " reports");
		System.out.println("training saw : " + trainingReportToSources.keySet().size() + " reports");
		Map<Integer, List<Double>> recalls = new HashMap<Integer, List<Double>>();
		Map<Integer, List<Double>> precisions = new HashMap<Integer, List<Double>>();
		Map<Integer, List<Double>> falsePositives = new HashMap<Integer, List<Double>>();
		for (String report : testingGoldenReportToSources.keySet()) { // new idea
		//for (String report : reportToPredictions.keySet()) { // way it's always been
		
			Map<Integer, Double> tmpSources = reportToPredictions.get(report);
			
			int totalPositiveToFind = testingGoldenReportToSources.get(report).size();
			int totalNegativeToFind = tmpSources.keySet().size() - totalPositiveToFind;
			
			int numReturned = 0;
			int numPositiveFound = 0;
			int numNegativeFound = 0;
			
			if (report.equals("D13-1109")) {
				System.out.println("totalpos to find: " + totalPositiveToFind);
			}
			Iterator it = sortByValueDescending(tmpSources).keySet().iterator();
			while (it.hasNext()) {
				String source = lineNumToSourceName.get((Integer)it.next());

				numReturned++;
				if (testingGoldenReportToSources.get(report).contains(source)) {
					numPositiveFound++;
				} else {
					numNegativeFound++;
				}
				
				double recall = (double)numPositiveFound / (double)totalPositiveToFind;
				double precision = (double)numPositiveFound / (double)numReturned;
				double falsePos = (double)numNegativeFound / (double)totalNegativeToFind;
				
				if (totalPositiveToFind < 1 || recall > 1) {
					System.err.println("totalPosFin: " + totalPositiveToFind + "; recalL: " + recall);
				}
				
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
		
		BufferedWriter avgG1 = new BufferedWriter(new FileWriter(outputDir + "avgG1-" + corpus + "-svm.csv"));
		/*
		avgG1.write("per SVM; # compared sources: " + comparedSources.size() + "\n");
		Collection<String> unsorted = comparedSources;
		List<String> sorted = asSortedList(unsorted);
		for (String s : sorted) {
			avgG1.write(s + "\n");
		}
		
		unsorted = new ArrayList<String>();
		for (Short s : topicReportToSources.get(docNameToID.get("D13-1109")).keySet()) {
			unsorted.add(docIDToName.get(s));
		}
		
		sorted = asSortedList(unsorted);
		avgG1.write("per LDA; # compared sources: " + sorted.size() + "\n");
		for (String s : sorted) {
			avgG1.write(s + "\n");
		}
		*/
		
		BufferedWriter avgG2 = new BufferedWriter(new FileWriter(outputDir + "avgG2-" + corpus + "-svm.csv"));
		BufferedWriter avgG3 = new BufferedWriter(new FileWriter(outputDir + "avgG3-" + corpus +  "-svm.csv"));
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
		double rate = (double)approxNumRows / (double)globalSourceProbs.keySet().size();	
		BufferedWriter globalG1 = new BufferedWriter(new FileWriter(outputDir + "globalG1-" + corpus + "-svm.csv"));
		BufferedWriter globalG2 = new BufferedWriter(new FileWriter(outputDir + "globalG2-" + corpus + "-svm.csv"));
	    globalG1.write("recall,precision\n");
	    globalG2.write("false_pos,true_pos\n");
		int totalPositiveToFind = 0; //reportToSources.get(report).size();
		int totalNegativeToFind = 0; //rankedSources.size() - totalPositiveToFind;
		for (String report : reportToPredictions.keySet()) {
			totalPositiveToFind += testingGoldenReportToSources.get(report).size();
			totalNegativeToFind += (reportToPredictions.get(report).size() - testingGoldenReportToSources.get(report).size());
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
			
			if (testingGoldenReportToSources.get(d1).contains(d2)) {
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

	private static void loadTopicModelObject(String to) {
		ObjectInputStream in;
		System.out.print("loading topic model and ranking... ");
		// reads in the TopicModelObject (i.e., mallet's LDA model's statistics like P(W|Z), P(Z|D))
		try {
			in = new ObjectInputStream(new FileInputStream(to));
			tmo = (TopicModelObject) in.readObject();
			
			allDocuments = new HashSet<String>();
			allDocuments = tmo.docToTopicProbabilities.keySet();
			
		} catch (IOException i) {
			i.printStackTrace();
			return;
		} catch (ClassNotFoundException c) {
			System.out.println("LDAObject class not found");
			c.printStackTrace();
			return;
		}
		
		System.out.println("# of all docs: " + allDocuments.size());
	}

	// creates SVM training, testing, and trainingTruth files
	private static void createSVMTrainingAndTest(String trainingFile, String testing, String truth) throws IOException {
		
		/*
		for (String s : docNameToID.keySet()) {
			System.out.println("docnames: " + s);
		}
		for (Short s : topicReportToSources.keySet()) {
			System.out.println("report ids: " + s);
		}
		*/
		System.out.println("*** creating training and test");
		BufferedWriter boutTrain = new BufferedWriter(new FileWriter(trainingFile));
		int numTrainingReports = 0;
		System.out.println("*** candidate sources size: " + candidateSources.size());
		
		numTopics = tmo.topicToWordProbabilities.keySet().size();
		// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
		System.out.println("* preprocessing P(S|Z)...");
		topicSums = new Double[numTopics];
		for (int t=0; t<numTopics; t++) {
			double totalSum = 0.0;
			
			for (String candidateDoc : candidateSources /*sources*/) {
				
				double prob_source = 0.01;
				if (citedCounts.containsKey(candidateDoc)) {
					prob_source += (double)citedCounts.get(candidateDoc);
				}
				prob_source /= (double)totalCitations; //1; // uniform
				double prob_topic_given_source = tmo.docToTopicProbabilities.get(candidateDoc)[t];
				totalSum += (prob_topic_given_source * prob_source);
			}
			topicSums[t] = totalSum;
			System.out.println("topicSums[" + t + "]:" + topicSums[t]);
		}
		System.out.println("done!");
		
		
		for (String report : trainingReportToSources.keySet()) {
			
			//System.out.println("looking for " + report + "... id = " + docNameToID.get(report));
			String year = docIDToMeta.get(report).year;
		
					
			int numGoldSources = trainingReportToSources.get(report).size();
			
			int numNegativeSources = numGoldSources * posToNegRatio;
			
			List<String> foundGood = new ArrayList<String>();
			List<String> foundBad = new ArrayList<String>();
			
			//Map<Short, Float> topicSourceProbs = topicReportToSources.get(docNameToID.get(report));
			//Map<String, Double> pmtlmSourceProbs = pmtlmReportToSources.get(report);
			Map<String, Double> sourceProbs = getLDASourceProbs(report);
			
			Set<String> targetSources = trainingReportToSources.get(report);
			
			//System.out.println("report : " + report + " contained:" + sourceProbs.keySet().size() + " candidate sources/scores");
			
			// adds the good sources
			for (String source : targetSources) {
				if (sourceProbs.containsKey(source)) { // && pmtlmSourceProbs.containsKey(source)) {
					foundGood.add(source);
				} else {
					System.err.println("uh-oh!  lda and/or pmtlm doesn't have truth source: " + source + " for report: " + report);
				}
			}
			
			
			// for speed, let's convert the set (iterator) to a list, so we can access O(1)
			String[] tmpSources = new String[sourceProbs.keySet().size()];
			Iterator it = sourceProbs.keySet().iterator();
			int i=0;
			while (it.hasNext()) {
				tmpSources[i] = (String)it.next();
				i++;
			}
					
			Random rand = new Random();
			
			// adds randomly-chosen bad sources
			while (foundBad.size() < numNegativeSources) {

				int indexPos = rand.nextInt(tmpSources.length);
				//Iterator it = topicSourceProbs.keySet().iterator();
				String source = "";
				
				if (indexPos < tmpSources.length) {
					source = tmpSources[indexPos];
				} else {
					continue;
				}
				if (!targetSources.contains(source) && !source.equals("") && !source.equals(report) && 
				!(testingGoldenReportToSources.containsKey(report) && testingGoldenReportToSources.get(report).contains(source))
				/* && Integer.parseInt(docIDToMeta.get(source).year) <= Integer.parseInt(year)*/) {
					foundBad.add(source);
				}
			}

			if (foundGood.size() != targetSources.size()) {
				System.out.println("report " + report + " had " + foundGood.size() + " sources but there are " + targetSources.size() + " ideally");
			}
			
			// writes the training files for this report
			for (String source : foundGood) {
				String features = createFeatures(true, report, source, sourceProbs.get(source)); //, pmtlmSourceProbs.get(source));
				boutTrain.write(features + "\n");
			}
			for (String source : foundBad) {
				String features = createFeatures(true, report, source, sourceProbs.get(source)); //, pmtlmSourceProbs.get(source));
				boutTrain.write(features + "\n");
			}
			if (report.equals("P89-1018")) {
				System.out.println("report p89-1018 has " + foundGood.size() + "positive and " + foundBad.size() + " negative training sources");
			}
			numTrainingReports++;
		} // end of looping through each year
		boutTrain.close();
		
		// write the testing features file and testing truth file
		BufferedWriter boutTest = new BufferedWriter(new FileWriter(testing));
		BufferedWriter boutTruth = new BufferedWriter(new FileWriter(truth));
		int numTestingReports = 0;
		

		/*
		Set<String> testingReports = new HashSet<String>();
		for (String eugeneReport : eugeneReports) {
			testingReports.add(eugeneReport);
		}
		*/
		//testingReports.add("P05-1022");
		
		// this block adds all 2013 reports to testing too
		/*
		for (String report : reportToSources.keySet()) {
			String year = docToMeta.get(report).year;
			if (year.equals("2013")) {
				testingReports.add(report);
			}
		}
		*/
		
		for (String report : testingGoldenReportToSources.keySet()) {
			//System.out.println("creating test for report: " + report);
			List<String> foundGood = new ArrayList<String>();
			
			Map<String, Double> sourceProbs = getLDASourceProbs(report);
			//Map<String, Double> pmtlmSourceProbs = pmtlmReportToSources.get(report);
			
			Set<String> targetSources = testingGoldenReportToSources.get(report);
			
			// goes through all sources (per linkLDA's)
			Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
			int numCandidateSources = 0;
			while (it.hasNext()) {
				
				//Short sID = (Short)it.next();
				String candidateDoc = (String)it.next();
				
				// don't evaluate report-sources which are the same doc or have been seen during training
				if (report.equals(candidateDoc) ||
				(trainingReportToSources.containsKey(report) && trainingReportToSources.get(report).contains(candidateDoc))) {
					continue;
				}
				numCandidateSources++;
				MetaDoc mdSource = docIDToMeta.get(candidateDoc);
				if (targetSources.contains(candidateDoc)) {
					foundGood.add(candidateDoc);
					boutTruth.write(report + " " + candidateDoc + " *\n");
				} else {
					boutTruth.write(report + " " + candidateDoc + "\n");
				}
				
				// writes the testing files for this report
				String features = createFeatures(false, report, candidateDoc, sourceProbs.get(candidateDoc)); //, pmtlmSourceProbs.get(source));
				boutTest.write(features + "\n");
				
			}
			if (report.equals("P89-1018")) {
				System.out.println("p89-1018 during test-time compared " + numCandidateSources + " sources");
			}
		
			if (foundGood.size() != targetSources.size()) {
				System.out.println("testing report " + report + " had " + foundGood.size() + " sources but there are " + targetSources.size() + " ideally");
			}
			numTestingReports++;
		} // end of looping through all test reports
		
		boutTest.close();
		boutTruth.close();
		
		System.out.println("# reports we'll use for training:" + numTrainingReports);
		System.out.println("# reports we'll use for testing:" + numTestingReports);
		
	}

	public static double getBayesianScore(String report, String source) {
		double score = 0.0;

		for (int t=0; t<numTopics; t++) {
			
			double prob_source = 0.001; //0.0; //5.0; // TODO: fux with this
			if (citedCounts.containsKey(source)) {
				prob_source += (double)citedCounts.get(source);  //1; // uniform
			}
			prob_source /= (double)totalCitations;
			double prob_topic_given_source = tmo.docToTopicProbabilities.get(source)[t];
			double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];

			double prob_topic_given_report = tmo.docToTopicProbabilities.get(report)[t];
			
			score += (prob_source_given_topic * prob_topic_given_report);
		}
		return score;
	}
	
	
	// calculates P(S|R), for the passed-in R, via LDA
	private static Map<String, Double> getLDASourceProbs(String report) {
		Map<String, Double> sourceProbs = new HashMap<String, Double>();
		for (String candidateDoc : candidateSources) {
			
			// skips evaluating the report itself, along with any gold sources seen during training
			if (candidateDoc.equals(report)) {
				continue;
			}

			// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
			double score = getBayesianScore(report, candidateDoc);
			sourceProbs.put(candidateDoc, score);
		}
		
		return sourceProbs;
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
	private static String createFeatures(boolean isTraining, String report, String source, Double topicProb) { //, Double pmtlmProb) {
		String ret = "";
		MetaDoc mdReport = docIDToMeta.get(report);
		MetaDoc mdSource = docIDToMeta.get(source);
		boolean isPositive = false;
		int reportYear = Integer.parseInt(mdReport.year);
		if (isTraining && trainingReportToSources.containsKey(report) && trainingReportToSources.get(report).contains(source)) {// ||
			ret += "+1 ";
			isPositive = true;
		} else if (!isTraining && testingGoldenReportToSources.containsKey(report) && testingGoldenReportToSources.get(report).contains(source)) {
			ret += "+1 ";
		} else {
			ret += "-1 ";
		}
		
		// 1 if any author in the given report has cited this doc from a previous report,
		// where 'previous' just means another doc up to the same year that isn't this current report
		boolean prevCitedThisSource = false;
		boolean prevCitedAnAuthor = false;
		int numAuthorsOverlap = 0;
		for (String author : mdReport.names) {
			if (!prevCitedThisSource) {
				for (String authReport : authorToReports.get(author)) {
					MetaDoc md2 = docIDToMeta.get(authReport);
					int report2Year = Integer.parseInt(md2.year);
					if (!report.equals(authReport)) { // && /*report2Year <= reportYear */ 
						
						// checks if the author has cited this paper before
						if (trainingReportToSources.containsKey(authReport) && trainingReportToSources.get(authReport).contains(source)) {
							prevCitedThisSource = true;
							if (authorToReports.get(author).size() == 1) {
								System.out.println("**** AUTH: " + author + " only has 1 report!!");
								System.exit(1);
							}
							//System.out.println(isPositive + "auth: " + author + " (has " + authorToReports.get(author).size() + "); report: " + report + "(" + reportYear + ") has author: " + author + " whom prev cited " + source + " in his report: " + authReport + " which was written in " + report2Year);
							break;
						}
	
						// checks if the author has prev cited any of the source authors before
						/*
						if (trainingReportToSources.containsKey(authReport)) {
							for (String sourcesPrevCited : trainingReportToSources.get(authReport)) {
								if (docIDToMeta.containsKey(sourcesPrevCited)) {
									MetaDoc md3 = docIDToMeta.get(sourcesPrevCited);
									for (String authorSource : md3.names) {
										if (mdSource.names.contains(authorSource)) {
											prevCitedAnAuthor = true;
										}
									}
								}
							}
						}
						*/
					}
				}
			}
			// counts how many authors in the current report are also in the candidate source
			if (mdSource.names.contains(author)) {
				numAuthorsOverlap++;
			}
		}
		
		ret += "1:" + topicProb + " "; 
		
		
		if (prevCitedThisSource) { 
			ret += "2:1 ";
		} else {
			ret += "2:0 ";
		}
		
		
		// TODO: THIS IS WHERE I LEFT OFF!
		
		// 1 for the most cited, everything else normalized to this
		double numTimesCited = 0.1;
		if (citedCounts.containsKey(source)) {
			numTimesCited += citedCounts.get(source);
		}
		double citeNormalized = (double)numTimesCited / (double)highestCount;
		//ret += "3:" + citeNormalized + " ";
		
		Set<String> reportTitleWords = getImportantTitleWords(mdReport.title);
		Set<String> sourceTitleWords = getImportantTitleWords(mdSource.title);
		int intersection = 0;
		for (String word : reportTitleWords) {
			if (sourceTitleWords.contains(word)) {
				intersection++;
			}
		}
		
		int union = reportTitleWords.size() + sourceTitleWords.size() - intersection;
		double jaccard = (double)intersection / (0.001 + (double)union);
		//ret += "4:" + jaccard + " "; // was 3
		
		
		// + " "; // was 4
		
		//ret += "5:" + pmtlmProb; 
		/*
		if (prevCitedAnAuthor) {
			ret += "4:1 ";
		} else {
			ret += "4:0 ";
		}
		*/
		//ret += "5:" + numAuthorsOverlap;
		
		return ret;
	}

	
	private static Set<String> getImportantTitleWords(String title) {
		Set<String> ret = new HashSet<String>();
		StringTokenizer st = new StringTokenizer(title);
		while (st.hasMoreTokens()) {
			String word = (String)st.nextToken().toLowerCase();
			//if (tmo.wordToID.containsKey(word.toLowerCase())) {
			if (!stopwords.contains(word)) {
				ret.add(word);
			}
		}
		return ret;
	}
	
	/*
	private static Map<Short, Map<Short, Float>> loadScores(String sf) throws IOException {
		Map<Short, Map<Short, Float>>  ret = new HashMap<Short, Map<Short, Float>>();
		BufferedReader bin = new BufferedReader(new FileReader(sf));
		String curLine = "";
		int lineNum = 0;
		while ((curLine = bin.readLine())!=null) {
			
			if (lineNum % 100000 == 0) {
				System.out.println("line: " + lineNum);
			}
			lineNum++;
			
			String[] tokens = curLine.split(" ");
			
			
			String report = tokens[0];
			String source = tokens[1];
			float score = Float.parseFloat(tokens[2]);
			
			short reportID = (short) docNameToID.keySet().size();
			if (docNameToID.containsKey(report)) {
				reportID = docNameToID.get(report);
			} else {
				docNameToID.put(report, reportID);
				docIDToName.put(reportID, report);
			}
			short sourceID = (short) docNameToID.keySet().size();
			if (docNameToID.containsKey(source)) {
				sourceID = docNameToID.get(source);
			} else {
				docNameToID.put(source, sourceID);
				docIDToName.put(sourceID, source);
			}
			
			Map<Short, Float> tmpscores = new HashMap<Short, Float>();
			if (ret.containsKey(reportID)) {
				tmpscores = ret.get(reportID);
			}
			tmpscores.put(sourceID, score);
			ret.put(reportID, tmpscores);
		}
		return ret;
	}
	*/
/*	
	private static void loadTopicModelAndScoreReports(String tm) {
		ObjectInputStream in;
		System.out.print("loading topic model and ranking... ");
		// reads in the TopicModelObject (i.e., mallet's LDA model's statistics like P(W|Z), P(Z|D))
		try {
			in = new ObjectInputStream(new FileInputStream(tm));
			tmo = (TopicModelObject) in.readObject();
			numTopics = tmo.topicToWordProbabilities.keySet().size();

			// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
			topicSums = new Double[numTopics];
			for (int t=0; t<numTopics; t++) {
				double totalSum = 0.0;
				for (String candidateDoc : testingDocuments) {
					
					double prob_source = 0.01;
					if (citedCounts.containsKey(candidateDoc)) {
						prob_source += (double)citedCounts.get(candidateDoc);
					}
					prob_source /= (double)totalCitations; //1; // uniform
					double prob_topic_given_source = tmo.docToTopicProbabilities.get(candidateDoc)[t];
					totalSum += (prob_topic_given_source * prob_source);
				}
				topicSums[t] = totalSum;
			}

			in.close();
		} catch (IOException i) {
			i.printStackTrace();
			return;
		} catch (ClassNotFoundException c) {
			System.out.println("LDAObject class not found");
			c.printStackTrace();
			return;
		}

		// now ranks the sources for each report
		for (String report : goldenReportToSources.keySet()) {

			// determines P(S|R) for all sources which aren't the report
			Map<String, Double> sourceProbs = new HashMap<String, Double>();
			for (String candidateDoc : testingDocuments) {
				// skips evaluating the report itself, along with any gold sources seen during training
				if (candidateDoc.equals(report) || (trainingReportToSources.containsKey(report) && trainingReportToSources.get(report).contains(candidateDoc)) || (!sources.contains(candidateDoc))) {
					continue;
				}

				// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
				double score = 0.0;

				for (int t=0; t<numTopics; t++) {
					
					double prob_source = 1; //0.0; //5.0; // TODO: fux with this
					if (citedCounts.containsKey(candidateDoc)) {
						prob_source += (double)citedCounts.get(candidateDoc);  //1; // uniform
					}
					prob_source /= (double)totalCitations;
					double prob_topic_given_source = tmo.docToTopicProbabilities.get(candidateDoc)[t];
					double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];

					double prob_topic_given_report = tmo.docToTopicProbabilities.get(report)[t];

					score += (prob_source_given_topic * prob_topic_given_report);
				}
				sourceProbs.put(candidateDoc, score);
			}
			topicReportToSources.put(report, sourceProbs);
		} // end of going through all testing reports
	}*/

	private static void loadTrainingAndTestingCites(String training, String testing) throws IOException {
		citedCounts = new HashMap<String, Integer>();
		trainingReportToSources = new HashMap<String, Set<String>>();
		testingDocuments = new HashSet<String>();
		
		BufferedReader bin = new BufferedReader(new FileReader(training));
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			String[] tokens = curLine.split(" ");
			String report = tokens[1];
			String source = tokens[0];
			
			//allDocuments.add(report);
			//allDocuments.add(source);
			candidateSources.add(source);
			
			
			Set<String> tmp = new HashSet<String>();
			if (trainingReportToSources.containsKey(report)) {
				tmp = trainingReportToSources.get(report);
			}
			tmp.add(source);
			trainingReportToSources.put(report, tmp);
			
			// updates the # of times the source was cited during training
			int count = 0;
			if (citedCounts.containsKey(source)) {
				count = citedCounts.get(source);
			}
			count++;
			citedCounts.put(source, count);
			
			if (count > highestCount) {
				highestCount = count;
			}
			
			// updates totalCitations
			totalCitations++;
		}
		
		
		bin = new BufferedReader(new FileReader(testing));
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String source = st.nextToken();
			String report = st.nextToken();
			
			candidateSources.add(source);
			
			testingDocuments.add(report);
			testingDocuments.add(source);
			
			//allDocuments.add(report);
			//allDocuments.add(source);
			
			Set<String> curSources = new HashSet<String>();
			if (testingGoldenReportToSources.containsKey(report)) {
				curSources = testingGoldenReportToSources.get(report);
			}
			curSources.add(source);
			testingGoldenReportToSources.put(report, curSources);
		}
		/*
		// ensures the train doesn't contain any test
		for (String r : trainingReportToSources.keySet()) {
			for (String s : trainingReportToSources.get(r)) {
				if (testingGoldenReportToSources.containsKey(r) && testingGoldenReportToSources.get(r).contains(s)) {
					System.err.println("*** train contains a test doc: " + r + " -> " + s);
				}
			}
		}
		
		// ensures hte test doesn't contain any train
		for (String r : testingGoldenReportToSources.keySet()) {
			for (String s : testingGoldenReportToSources.get(r)) {
				if (trainingReportToSources.containsKey(r) && trainingReportToSources.get(r).contains(s)) {
					System.err.println("*** test contains a train doc: " + r + " -> " + s);
				}
			}
		}
		*/
		System.out.println("highest count amongst sources from docLegend: " + highestCount);
		System.out.println("# reports to test: " + testingGoldenReportToSources.keySet().size());
		//System.exit(1);
	}

	// stores all doc names so that we don't store ALL metadocs
	private static void loadDocNames(String f) throws IOException {
		BufferedReader bin = new BufferedReader(new FileReader(f));
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();
			allDocuments.add(docName);
		}
	}
	
	private static void loadMetaDocs(String mf) throws IOException {
		docIDToMeta = new HashMap<String, MetaDoc>();
		BufferedReader bin = new BufferedReader(new FileReader(mf));
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
			
			if (!allDocuments.contains(docID)) {
				continue;
			}
			// separates authors by ';' and forces matching on the exact string
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
			docIDToMeta.put(docID, md);
			
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
		
		for (String author : authorToReports.keySet()) {
			int numInTraining = 0;
			for (String rep : authorToReports.get(author)) {
				if (trainingReportToSources.containsKey(rep)) {
					numInTraining++;
				}
			}
			if (numInTraining > 0) {
				System.out.println(author + " has " + authorToReports.get(author).size() + " reports in our training");
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
	
	public static
	<T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
	  List<T> list = new ArrayList<T>(c);
	  java.util.Collections.sort(list);
	  return list;
	}
}

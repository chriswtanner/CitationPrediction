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


public class SVMHelper {

	// input files
	static String docsPath = "/Users/christanner/research/data/aan/dualformat/";
	static String docsLegend = "/Users/christanner/research/projects/CitationFinder/input/docsLegend-all.txt"; 
	static String referentialFile = "/Users/christanner/research/projects/CitationFinder/input/referential-all.txt";
	static String aclNetworkFile = "/Users/christanner/research/data/aan/release/2013/acl.txt";
	static String aclMetaFile = "/Users/christanner/research/data/aan/release/2013/acl-metadata.txt";
	static String ldaInput = "/Users/christanner/research/projects/CitationFinder/input/lda_200z_5000i.ser";		
	
	// output
	static String outputDir = "/Users/christanner/research/projects/CitationFinder/output/";
	static String trainingFile = "/Users/christanner/research/projects/CitationFinder/output/svm_training_features.txt";
	static String testingFile = "/Users/christanner/research/projects/CitationFinder/output/svm_testing_features.txt";
	static String testingTruth = "/Users/christanner/research/projects/CitationFinder/output/svm_testing_truth.txt";
	
	static String authorFile = "/Users/christanner/research/projects/CitationFinder/output/svm_author_plot.csv";
	static String citedPopFile = "/Users/christanner/research/projects/CitationFinder/output/svm_citedPop_plot.csv";
	static String jaccardFile = "/Users/christanner/research/projects/CitationFinder/output/svm_jaccard_plot.csv";

	// files for evaluation
	static String evaluateTruth = "/Users/christanner/research/projects/CitationFinder/output/svm_testing_truth.txt";
	static String evaluatePredictions = "/Users/christanner/research/projects/CitationFinder/output/testing.predictions";
	static boolean evaluateSVM = true;
	static boolean evaluateLDA = false;
	
	static TopicModelObject lda = null;
	static int numTopics = 0;
	static int posToNegRatio = 5;
	static int highestCount = 0;
	
	static List<Integer> numSourcesCutoffs = null;
	static Set<String> reportNames = new HashSet<String>();
	
	static Set<String> sourceNames = new HashSet<String>();
	static Set<String> corpus = new HashSet<String>();
	
	static Map<String, Set<String>> reportToSources = new HashMap<String, Set<String>>();
	static Map<String, Set<String>> aclNetwork = new HashMap<String, Set<String>>();
	static Map<String, Integer> citedCounts = new HashMap<String, Integer>(); // counts how many times the key doc was CITED BY another doc
	static Map<String, MetaDoc> docToMeta = new HashMap<String, MetaDoc>();
	static Map<String, Set<String>> authorToReports = new HashMap<String, Set<String>>();
	static Double[] topicSums = null;
	
	static int totalCitations = 0;
	
	static List<Integer> sourcesCutoffs = Arrays.asList(1,10,25,50,100,150,200,250,300,350,400,450,500,1000,2000,4000,10000);
	static int[][][] blah = new int[3][4][4];
	static List<String> eugeneReports = Arrays.asList("D10-1066", "P05-1022", "E09-1018");
	public static void main(String[] args) throws IOException {
		
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
		
		System.out.println("# of reportnames:" + reportNames.size());
		System.out.println("# of sourcenames:" + sourceNames.size());
		
		// reads in the total ACL (referential-type file);
		// NOTE: we only care to use this for determining if an author
		// has previously cited a paper or not.  otherwise, the only
		// report -> source info we have is based on our heavily-filtered
		// referential file, which has requirements for each report and the sources it cites,
		// and thus is an incomplete list.  when we're checking to see if an
		// author has previously cited a paper, we don't care if it meets these requirements;
		// thus, let's look at the original acl network file
		bin = new BufferedReader(new FileReader(aclNetworkFile));
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			// ensures we have report ==> source
			if (st.countTokens() != 3) {
				continue;
			}
			
			String report = st.nextToken();
			st.nextToken();
			String source = st.nextToken();
			
			// updates our map of Report -> {sources}
			Set<String> curSources = new HashSet<String>();
			if (aclNetwork.containsKey(report)) {
				curSources = aclNetwork.get(report);
			}
			if (!report.equals(source)) {
			curSources.add(source);
			}
			aclNetwork.put(report, curSources);
		}
		
		// reads in the referential file
		bin = new BufferedReader(new FileReader(referentialFile));
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String report = st.nextToken();
			String source = st.nextToken();
			
			Set<String> curSources = new HashSet<String>();
			if (reportToSources.containsKey(report)) {
				curSources = reportToSources.get(report);
			}
			curSources.add(source);
			reportToSources.put(report, curSources);
			
			// updates the citation counts
			if (citedCounts.containsKey(source)) {
				citedCounts.put(source, citedCounts.get(source)+1);
			} else {
				citedCounts.put(source, 1);
			}
			totalCitations++;
		}
		
		
		System.out.println("# of reports (based on referential file): " + reportToSources.keySet().size());
		
		//Map<String, Set<MetaDoc>> yearToMetaDocs = new HashMap<String, Set<MetaDoc>>();
		authorToReports = new HashMap<String, Set<String>>();
		
		// loads the MetaDocs
		docToMeta = new HashMap<String, MetaDoc>();
		bin = new BufferedReader(new FileReader(aclMetaFile));
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
			
			// separates authors by ';' and forces matching on the exact string
			String authorLine = bin.readLine().toLowerCase();
			authorLine = authorLine.substring(authorLine.indexOf("{")+1).replace("}", "");
			st = new StringTokenizer(authorLine, ";");
			
			List<String> authorTokens = new ArrayList<String>();
			while (st.hasMoreTokens()) {
				String authorToken = st.nextToken();
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
			
			MetaDoc md = new MetaDoc(docID, title, year, authorTokens);
			docToMeta.put(docID, md);
			
			// updates the yearToMetaDoc map
			/*
			if (reportNames.contains(docID)) {
				Set<MetaDoc> tmpMetadocs = new HashSet<MetaDoc>();
				if (yearToMetaDocs.containsKey(year)) {
					tmpMetadocs = yearToMetaDocs.get(year);
				}
				tmpMetadocs.add(md);
				yearToMetaDocs.put(year, tmpMetadocs);
			}
			*/
			
			// updates each author in this report to include this report
			for (String author : authorTokens) {
				Set<String> tmpReports = new HashSet<String>();
				if (authorToReports.containsKey(author)) {
					tmpReports = authorToReports.get(author);
				}
				
				// adds to the curReports
				// TODO: originally, this wasn't commented, but i suppose we should care
				// about when an author is cited within all docs, not just the super clean 'reports'
				if (aclNetwork.containsKey(docID)) {
					tmpReports.add(docID);
				}
				authorToReports.put(author, tmpReports);
				
				if (author.equals("charniak, eugene")) {
					if (reportToSources.containsKey(docID)) {
						System.out.println("eugene's " + docID + " paper IS in our reports ***");
					} else {
						System.out.println("eugene's " + docID + " paper IS NOT in our reports");
					}
				}
			}
		} // end of going through metadocs file
		
		//System.out.println("# of authors < 2013: " + authorToReports.keySet().size());
		
		// displays # of reports per year
		//System.out.println("# of distinct years with valid reports: " + yearToMetaDocs.keySet().size());
		/*
		SortedSet<String> sortedYears = new TreeSet<String>(yearToMetaDocs.keySet());
		for (String year : sortedYears) {
			System.out.println(year + " has " + yearToMetaDocs.get(year).size());
		}
		*/
		highestCount = 0;
		for (String doc : citedCounts.keySet()) {
			if (sourceNames.contains(doc) && (citedCounts.get(doc) > highestCount)) {
				highestCount = citedCounts.get(doc);
			}
		}
		System.out.println("highest count amongst sources from docLegend: " + highestCount);
		
		
		// reads in the LDAObject (i.e., mallet's LDA model's statistics like P(W|Z), P(Z|D))
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(ldaInput));
			lda = (TopicModelObject) in.readObject();
			numTopics = lda.topicToWordProbabilities.keySet().size();
			
			// pre-processes by 1st calculating sum_s' [ P(Z|S')P(S') ] for every (s,z) pair and saves it
			System.out.println("* preprocessing P(S|Z)...");
			topicSums = new Double[numTopics];
			for (int t=0; t<numTopics; t++) {
				double totalSum = 0.0;
				for (String source : sourceNames) {
					double prob_source = (double)citedCounts.get(source) / (double)totalCitations; //1; // uniform
					double prob_topic_given_source = lda.docToTopicProbabilities.get(source)[t];
					totalSum += (prob_topic_given_source * prob_source);
				}
				topicSums[t] = totalSum;
			}
			System.out.println("done!");
			
			in.close();
		} catch (IOException i) {
			i.printStackTrace();
			return;
		} catch (ClassNotFoundException c) {
			System.out.println("LDAObject class not found");
			c.printStackTrace();
			return;
		}
		
		System.out.println("report P05-1022 has " + reportToSources.get("P05-1022").size() + " gold sources");
		MetaDoc tmp = docToMeta.get("P05-1022");
		int reportYear = Integer.parseInt(tmp.year);
		for (String author : tmp.names) {
			System.out.println("author: " + author);
			
			Set<String> citedDocs = new HashSet<String>();
			
			for (String authReport : authorToReports.get(author)) {
				MetaDoc md2 = docToMeta.get(authReport);
				int report2Year = Integer.parseInt(md2.year);
				System.out.println("\tco-authored: " + md2.docID + " (" + md2.title + " [" + md2.year + "])");
				if (true) { //report2Year <= reportYear) { // && !authReport.equals("P05-1022")) {
					System.out.println("\twhich cites:");

					for (String source : aclNetwork.get(authReport)) {
						System.out.print("\t\t" + source);
						if (docToMeta.containsKey(source)) {
							System.out.println(" (" + docToMeta.get(source).title + " [" + docToMeta.get(source).year + "])");
						} else {
							System.out.println("");
						}
					}
					
					if (reportToSources.containsKey(authReport)) {
						System.out.println("\t*** we have the report!");
					} else {
						System.out.println("\twe don't have the report");
					}
				}
			}
		}
		//System.exit(1); 
		
		if (evaluateSVM || evaluateLDA) {
			Map<Integer, String> lineNumToReportName = new HashMap<Integer, String>();
			Map<Integer, String> lineNumToSourceName = new HashMap<Integer, String>(); 
			Map<String, Map<Integer, Double>> reportToPredictions = new HashMap<String, Map<Integer, Double>>();

			BufferedReader binTruth = new BufferedReader(new FileReader(evaluateTruth));
			curLine = "";
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
			
			BufferedReader binPredictions = new BufferedReader(new FileReader(evaluatePredictions));
			lineNum = 1;
			binPredictions.readLine(); // skips past the header of labels
			while ((curLine = binPredictions.readLine())!=null) {
				StringTokenizer st = new StringTokenizer(curLine);
				st.nextToken(); // skips past the label prediction (we care about the probabilities)
				double prob = Double.parseDouble(st.nextToken());
				
				Map<Integer, Double> predictions = new HashMap<Integer, Double>();
				String report = lineNumToReportName.get(lineNum);
				if (reportToPredictions.containsKey(report)) {
					predictions = reportToPredictions.get(report);
				}
				predictions.put(lineNum, prob);
				reportToPredictions.put(report, predictions);
				lineNum++;
			}
			
			List<Double> recalls = new ArrayList<Double>();
			List<String> svmRankedSources = new ArrayList<String>();
			List<String> ldaRankedSources = getLDASourcePredictions("P05-1022", 10000);			
			
			System.out.println("reportToPredictions:" + reportToPredictions.keySet());
			
			for (int cutoffPoint : sourcesCutoffs) {
				
				double totalRecallAvgs = 0;

				// goes through each Report's predictions
				int numReportsEval = 0;
				for (String report : reportToPredictions.keySet()) {
					
					List<String> ldaRankedSources2 = getLDASourcePredictions(report, cutoffPoint);
					
					// gets the valid, truth Sources
					if (!reportToSources.containsKey(report)) {
						System.out.println("we dont have report:" + report);
						continue;
					}
					Set<String> goldSources = reportToSources.get(report);
					double totalValid = goldSources.size();
					double foundValid = 0;
					double foundInvalid = 0;
					
					if (totalValid == 0) {
						continue;
					}

					// svm way
					if (evaluateSVM) {
						
						// gets predictions
						Map<Integer, Double> predictions = reportToPredictions.get(report);
						Iterator it = sortByValueDescending(predictions).keySet().iterator();
						
						int i=0;
						while (it.hasNext() && i < cutoffPoint) {
							lineNum = (Integer)it.next();
							String predictedSource = lineNumToSourceName.get(lineNum);
							
							if (cutoffPoint == 10000 && report.equals("P05-1022")) {
								svmRankedSources.add(predictedSource);
								//System.out.println("source: " + predictedSource + " = " + predictions.get(lineNum));
							}
							
							if (goldSources.contains(predictedSource)) {
								foundValid++;
							} else {
								foundInvalid++;
							}
							i++;
						}
						totalRecallAvgs += ((double)foundValid / (double)totalValid);
						numReportsEval++;
					}
					
					// lda way
					if (evaluateLDA) {
						Iterator it = ldaRankedSources2.iterator();
						int i=0;
						while (it.hasNext() && i < cutoffPoint) {
							String predictedSource = (String)it.next();
							
							if (goldSources.contains(predictedSource)) {
								foundValid++;
							} else {
								foundInvalid++;
							}
							i++;
						}
						totalRecallAvgs += ((double)foundValid / (double)totalValid);
						numReportsEval++;
					}
				} // end of all Reports

				double recall = totalRecallAvgs / (double)numReportsEval;
				recalls.add(recall);
			} // end of going through all cutoff points
			System.out.println("cutoff points:" + sourcesCutoffs);
			System.out.println(recalls);
			
			// prints in x,y format for making a graph
			for (int i=0; i<sourcesCutoffs.size(); i++) {
				System.out.println(recalls.get(i));
			}
			
			// prints svmRankedSources and ldaRankedSources
			for (String eugeneReport : eugeneReports) {
				
				svmRankedSources = new ArrayList<String>();
				Map<Integer, Double> predictions = reportToPredictions.get(eugeneReport);
				Iterator it = sortByValueDescending(predictions).keySet().iterator();
				
				int i=0;
				while (it.hasNext() && i < 9000) {
					lineNum = (Integer)it.next();
					String predictedSource = lineNumToSourceName.get(lineNum);
					svmRankedSources.add(predictedSource);
				}
				printRankedSources(eugeneReport, svmRankedSources, outputDir + eugeneReport + "_svm_predictions.txt");
				//printRankedSources("P05-1022", ldaRankedSources, ldaPredictionsFile);		
				
				DecimalFormat df = new DecimalFormat("##.##");
				// prints the features of each of the good sources of P05-1022
				System.out.println("\n\nsource, author-based rank, lda rank, lda prob, titleSimilarity, prevCited?, prevAuthorCited?, authorOverlap, citedPop, title\n");
				Map<String, Double> sourceProbs = getLDAProbs(eugeneReport);
				
				System.out.println("\n\n" + eugeneReport + " features:\n-----------------------");
				for (String source : reportToSources.get(eugeneReport)) {
					String features = createFeatures(eugeneReport, source, sourceProbs.get(source));
					StringTokenizer st = new StringTokenizer(features, ": ");
					/*
					st.nextToken(); // skips over label
					st.nextToken(); // skips 1:
					int prevCited = Integer.parseInt(st.nextToken());
					st.nextToken(); // skips 2:
					double citedPop = Double.parseDouble(st.nextToken());
					st.nextToken(); // skips 3:
					double jaccard = Double.parseDouble(st.nextToken());
					
					List<String> ldaRankedSources2 = getLDASourcePredictions(eugeneReport, 9000);
					
					st.nextToken(); // skips 4
					st.nextToken();
					st.nextToken(); // skips 5
					int prevAuthor = Integer.parseInt(st.nextToken());
					
					st.nextToken(); // skips 6
					int numAuthorOverlap = Integer.parseInt(st.nextToken());
					
					System.out.println(source + "," + svmRankedSources.indexOf(source) + "," + ldaRankedSources2.indexOf(source) + "," + sourceProbs.get(source) + "," + jaccard + "," + prevCited + "," + prevAuthor + "," + numAuthorOverlap + "," + citedPop + "," + docToMeta.get(source).title + " (" + docToMeta.get(source).year + ")");
					*/
				}
			}
			System.exit(1);
		} // end of evaluation
		
		// creates SVM training, testing, and trainingTruth files
		BufferedWriter boutTrain = new BufferedWriter(new FileWriter(trainingFile));
		int numTrainingReports = 0;
		for (String report : reportToSources.keySet()) {
			String year = docToMeta.get(report).year;
		
			if (!year.equals("2013")) {
				if (!eugeneReports.contains(report)) {//report.equals("P05-1022")) {
					
					int numGoldSources = reportToSources.get(report).size();
					//System.out.println(report + " has " + numGoldSources);
					
					int numNegativeSources = numGoldSources * posToNegRatio;
					
					List<String> foundGood = new ArrayList<String>();
					List<String> foundBad = new ArrayList<String>();
					
					Map<String, Double> sourceProbs = getLDAProbs(report);
					Set<String> targetSources = reportToSources.get(report);
					// adds the good sources
					for (String source : targetSources) {
						if (sourceProbs.containsKey(source)) {
							foundGood.add(source);
						}
					}
					
					// adds randomly-chosen bad sources
					while (foundBad.size() < numNegativeSources) {
						Random rand = new Random();
						int indexPos = rand.nextInt(sourceNames.size());
						Iterator it = sourceNames.iterator();
						String source = "";
						int i=0; 
						while (it.hasNext() && i<=indexPos) {
							source = (String)it.next();
							i++;
						}
						if (!source.equals("") && !source.equals(report) && 
						Integer.parseInt(docToMeta.get(source).year) < Integer.parseInt(year)) {
							foundBad.add(source);
						}
					}

					if (foundGood.size() != targetSources.size()) {
						System.out.println("report " + report + " had " + foundGood.size() + " sources but there are " + targetSources.size() + " ideally");
					}
					
					// writes the training files for this report
					for (String source : foundGood) {
						String features = createFeatures(report, source, sourceProbs.get(source));
						boutTrain.write(features + "\n");
					}
					for (String source : foundBad) {
						String features = createFeatures(report, source, sourceProbs.get(source));
						boutTrain.write(features + "\n");
					}
					numTrainingReports++;
				} // end of checking if we're a report
			} // end of checking if < 2013
		} // end of looping through each year
		boutTrain.close();
		
		// write the testing features file and testing truth file
		BufferedWriter boutTest = new BufferedWriter(new FileWriter(testingFile));		
		BufferedWriter boutTruth = new BufferedWriter(new FileWriter(testingTruth));
		int numTestingReports = 0;
		
		Set<String> testingReports = new HashSet<String>();
		for (String eugeneReport : eugeneReports) {
			testingReports.add(eugeneReport);
		}
		//testingReports.add("P05-1022");
		
		// this block adds all 2013 reports to testing too
		
		for (String report : reportToSources.keySet()) {
			String year = docToMeta.get(report).year;
			if (year.equals("2013")) {
				testingReports.add(report);
			}
		}
		
		
		for (String report : testingReports) {
			List<String> foundGood = new ArrayList<String>();
			Map<String, Double> sourceProbs = getLDAProbs(report);
			Set<String> targetSources = reportToSources.get(report);
			Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
			while (it.hasNext()) {
				String source = (String)it.next();
				if (report.equals(source)) {
					continue;
				}
				MetaDoc mdSource = docToMeta.get(source);
				if (targetSources.contains(source)) {
					foundGood.add(source);
					boutTruth.write(report + " " + source + " *\n");
				} else {
					boutTruth.write(report + " " + source + "\n");
				}
				
				// writes the testing files for this report
				String features = createFeatures(report, source, sourceProbs.get(source));
				boutTest.write(features + "\n");
				
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
		
		// gets author info for all cited docs and the top 200 non-cited sources per LDA's rankings
		/*
		Map<String, Integer> docToAuthorPrevCited = new HashMap<String, Integer>();
		Map<String, Double> docToJaccard = new HashMap<String, Double>();
		Map<String, Double> docToCitedPop = new HashMap<String, Double>();
		List<String> reportToPlot = Arrays.asList("P05-1022");
		BufferedWriter boutAuthor = new BufferedWriter(new FileWriter(authorFile));
		BufferedWriter boutCitedPop = new BufferedWriter(new FileWriter(citedPopFile));
		BufferedWriter boutJaccard = new BufferedWriter(new FileWriter(jaccardFile));
		
		double authorGoodCited = 0;
		double authorBadCited = 0;
		for (String report : reportToPlot) {
			
			if (!docToMeta.get(report).year.equals("2013")) {
				continue;
			}
			Set<String> goodSources = reportToSources.get(report);
			Set<String> badSources = new HashSet<String>();
			Map<String, Double> sourceProbs = getLDAProbs(report);
			
			Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
			while (it.hasNext() && badSources.size() < 9200) {
				String source = (String)it.next();
				if (report.equals(source)) {
					continue;
				}
				MetaDoc mdSource = docToMeta.get(source);
				if (!goodSources.contains(source)) {
					badSources.add(source);
				}
			}
			
			Set<String> sourcesToPlot = new HashSet<String>();
			sourcesToPlot.addAll(goodSources);
			sourcesToPlot.addAll(badSources);
			
			for (String source : sourcesToPlot) {
				String features = createFeatures(report, source, sourceProbs.get(source));
				StringTokenizer st = new StringTokenizer(features, ": ");
				st.nextToken(); // skips over label
				st.nextToken(); // skips 1:
				int prevCited = Integer.parseInt(st.nextToken());
				st.nextToken(); // skips 2:
				double citedPop = Double.parseDouble(st.nextToken());
				st.nextToken(); // skips 3:
				double jaccard = Double.parseDouble(st.nextToken());
				
				docToAuthorPrevCited.put(source, prevCited);
				docToCitedPop.put(source, citedPop);
				docToJaccard.put(source, jaccard);
			}
			
			boutAuthor.write("cited\n");
			boutCitedPop.write("cited\n");
			boutJaccard.write("cited\n");
			//double authorGoodCited = 0;
			double jaccardGoodCited = 0;
			for (String source : goodSources) {
				boutAuthor.write(sourceProbs.get(source) + "," + docToAuthorPrevCited.get(source) + "\n");
				boutCitedPop.write(sourceProbs.get(source) + "," + docToCitedPop.get(source) + "\n");
				boutJaccard.write(sourceProbs.get(source) + "," + docToJaccard.get(source) + "\n");
				
				if (docToAuthorPrevCited.get(source) == 1) {
					authorGoodCited++;
				}
				jaccardGoodCited += docToJaccard.get(source);
			}
			//System.out.println("author prev cited, within good:" + authorGoodCited);
			//authorGoodCited /= goodSources.size();
			jaccardGoodCited /= goodSources.size();
			
			boutAuthor.write("\n\n\n\n\n\n\nuncited\n");
			boutCitedPop.write("\n\n\n\n\n\n\nuncited\n");
			boutJaccard.write("\n\n\n\n\n\n\nuncited\n");
			
			//double authorBadCited = 0;
			double jaccardBadCited = 0;
			for (String source : badSources) {
				boutAuthor.write(sourceProbs.get(source) + "," + docToAuthorPrevCited.get(source) + "\n");
				boutCitedPop.write(sourceProbs.get(source) + "," + docToCitedPop.get(source) + "\n");
				boutJaccard.write(sourceProbs.get(source) + "," + docToJaccard.get(source) + "\n");
				
				if (docToAuthorPrevCited.get(source) == 1) {
					authorBadCited++;
				}
				jaccardBadCited += docToJaccard.get(source);
			}
			//System.out.println("author prev cited, within bad:" + authorBadCited);
			
			//authorBadCited /= badSources.size();
			jaccardBadCited /= badSources.size();
			//System.out.println("avg authorCited within good:" + authorGoodCited + " and bad:" + authorBadCited);
			//System.out.println("avg jaccardCited within good:" + jaccardGoodCited + " and bad:" + jaccardBadCited);
		}
		boutAuthor.close();
		boutCitedPop.close();
		boutJaccard.close();
		System.out.println("author prev cited, within good:" + authorGoodCited);
		System.out.println("author prev cited, within bad:" + authorBadCited);
		*/
	}

	private static void printRankedSources(String report, List<String> rankedSources, String output) throws IOException {
		BufferedWriter bout = new BufferedWriter(new FileWriter(output));
		
		Set<String> targetSources = reportToSources.get(report);
		
		bout.write("report:" + docToMeta.get(report).title + " (" + docToMeta.get(report).year + ")\n");
		bout.write("total valid sources:" + targetSources.size() + "\n\n");
		bout.write("pos cited? title (year)\n");
		
		// print the topic proportions: topic # (prob)
		DecimalFormat df = new DecimalFormat("##.##");
		Map<Integer, Double> tmp = new HashMap<Integer, Double>();
		for (int t=0; t<numTopics; t++) {
			tmp.put(t, lda.docToTopicProbabilities.get(report)[t]);
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
		
		int i=1;
		int numCorrect = 0;
		for (String source : rankedSources) {
			MetaDoc md = docToMeta.get(source);
			if (targetSources.contains(source)) {
				numCorrect++;

				bout.write(i + " * " + docToMeta.get(source).title + " (" + md.year + ") " + citedCounts.get(source) + "\n");
			} else {
				bout.write(i + "   " + docToMeta.get(source).title + " (" + md.year + ") " + citedCounts.get(source) + "\n");
			}
			
			// print the topic proportions: topic # (prob)
			tmp = new HashMap<Integer, Double>();
			for (int t=0; t<numTopics; t++) {
				tmp.put(t, lda.docToTopicProbabilities.get(source)[t]);
			}
			it3 = sortByValueDescending(tmp).keySet().iterator();
			j=0;
			bout.write("\t");
			while (it3.hasNext() && j < 10) {
				int topicNum = (Integer)it3.next();
				double prob = tmp.get(topicNum);
				bout.write(topicNum + " (" + df.format(prob) + ") ");
				j++;
			}
			bout.write("\n");
			i++;
		}
	}

	private static List<String> getLDASourcePredictions(String report, int cutoffPoint) {
		List<String> ret = new ArrayList<String>();
		
		int reportYear = Integer.parseInt(docToMeta.get(report).year);
				
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
				for (int t=0; t<numTopics; t++) {
					
					double prob_source = (double)citedCounts.get(source) / (double)totalCitations; //1; // uniform
					double prob_topic_given_source = lda.docToTopicProbabilities.get(source)[t];
					double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];
					
					double prob_topic_given_report = lda.docToTopicProbabilities.get(report)[t];
					score += (prob_source_given_topic * prob_topic_given_report);
				}
			}
			sourceProbs.put(source, score);
		}
		
		// sort sourceProbs in decreasing order
		Iterator it = sortByValueDescending(sourceProbs).keySet().iterator();
		int i=0;
		while (it.hasNext() && i < cutoffPoint) {
			String source = (String)it.next();
			ret.add(source);
			i++;
		}
		return ret;
	}

	// returns a feature vector for libSVM
	private static String createFeatures(String report, String source, double ldaProb) {
		String ret = "";
		MetaDoc mdReport = docToMeta.get(report);
		MetaDoc mdSource = docToMeta.get(source);
		
		int reportYear = Integer.parseInt(mdReport.year);
		if (reportToSources.get(report).contains(source)) {
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
			for (String authReport : authorToReports.get(author)) {
				MetaDoc md2 = docToMeta.get(authReport);
				int report2Year = Integer.parseInt(md2.year);
				if (report2Year <= reportYear && !report.equals(authReport)) {
					// checks if the author has cited this paper before
					if (aclNetwork.get(authReport).contains(source)) {
						prevCitedThisSource = true;
						//break;
					}
					
					// checks if the author has prev cited any of the source authors before
					for (String sourcesPrevCited : aclNetwork.get(authReport)) {
						if (docToMeta.containsKey(sourcesPrevCited)) {
							MetaDoc md3 = docToMeta.get(sourcesPrevCited);
							for (String authorSource : md3.names) {
								if (mdSource.names.contains(authorSource)) {
									prevCitedAnAuthor = true;
								}
							}
						}
					}
				}
			}
			
			// counts how many authors in the current report are also in the candidate source
			if (mdSource.names.contains(author)) {
				numAuthorsOverlap++;
			}
		}
		
		
		if (prevCitedThisSource) { 
			ret += "1:1 ";
		} else {
			ret += "1:0 ";
		}
		
		/*
		if (prevCitedAnAuthor) {
			ret += "1:1 ";
		} else {
			ret += "1:0 ";
		}
		*/
		//ret += "1:" + numAuthorsOverlap;
		
		// 1 for the most cited, everything else normalized to this
		int numTimesCited = citedCounts.get(source);
		double citeNormalized = (double)numTimesCited / (double)highestCount;
		//ret += "2:" + citeNormalized + " ";
		
		Set<String> reportTitleWords = getImportantTitleWords(mdReport.title);
		Set<String> sourceTitleWords = getImportantTitleWords(mdSource.title);
		int intersection = 0;
		for (String word : reportTitleWords) {
			if (sourceTitleWords.contains(word)) {
				intersection++;
			}
		}
		
		int union = reportTitleWords.size() + sourceTitleWords.size() - intersection;
		double jaccard = (double)intersection / (double)union;
		//ret += "3:" + jaccard + " "; // was 3
		
		
		ret += "2:" + ldaProb + " "; // was 4
		
		/*
		if (prevCitedAnAuthor) {
			ret += "4:1 ";
		} else {
			ret += "4:0 ";
		}
		
		ret += "5:" + numAuthorsOverlap;
		*/
		return ret;
	}

	private static Set<String> getImportantTitleWords(String title) {
		Set<String> ret = new HashSet<String>();
		StringTokenizer st = new StringTokenizer(title);
		while (st.hasMoreTokens()) {
			String word = (String)st.nextToken();
			if (lda.wordToID.containsKey(word.toLowerCase())) {
				ret.add(word.toLowerCase());
			}
		}
		return ret;
	}

	// returns (and optionally prints) the recall results for the passed-in file
	public static Map<String, Double> getLDAProbs(String report) throws IOException {
		Map<String, Double> sourceProbs = new HashMap<String, Double>();
		int reportYear = Integer.parseInt(docToMeta.get(report).year);
		
		for (String source : sourceNames) {
			if (source.equals(report)) {
				continue;
			}

			// calculates score = sum_z[ P(S|Z)*P(Z|R) ], where P(S|Z) =  P(Z|S)P(S) normalized
			double score = 0.0;
			
			int sourceYear = Integer.parseInt(docToMeta.get(source).year);
			
			if (sourceYear <= reportYear) {
				for (int t=0; t<numTopics; t++) {
					
					double prob_source = (double)citedCounts.get(source) / (double)totalCitations; //1; // uniform
					double prob_topic_given_source = lda.docToTopicProbabilities.get(source)[t];
					double prob_source_given_topic = (prob_topic_given_source * prob_source) / topicSums[t];
					
					double prob_topic_given_report = lda.docToTopicProbabilities.get(report)[t];
					score += (prob_source_given_topic * prob_topic_given_report);
				}
			}
			sourceProbs.put(source, score);
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
	
}

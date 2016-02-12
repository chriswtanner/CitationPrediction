import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import cc.mallet.types.FeatureVectorSequence.Iterator;


public class ACLMasterSplitter {

	// input 
	public static String referentialFile = "/Users/christanner/research/projects/CitationFinder/input/all_10k_docs/referential.txt";
	public static String malletInputFile = "/Users/christanner/research/projects/CitationFinder/input/all_10k_docs/mallet-input.txt";
	public static String metaDocsFile = "/Users/christanner/research/data/aan/release/2013/acl-metadata.txt";
	public static int maxDocsToCompriseCorpus = 500;
	
	public static double trainingPercentage = 0.9;
	
	// output
	public static String malletOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + "-mallet.txt";
	public static String contentOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".content";
	public static String trainingOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".training";
	public static String testingOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".testing";

	// global vars
	public static Map<String, Set<String>> allReportToSources = new HashMap<String, Set<String>>();
	public static Map<String, Set<String>> reportToSources = new HashMap<String, Set<String>>();
	public static Map<String, Set<String>> sourceToReports = new HashMap<String, Set<String>>();
	public static Set<String> allDocuments = new HashSet<String>(); // stores docs' names (reports and sources)
	private static Map<String, MetaDoc> docIDToMeta = new HashMap<String, MetaDoc>();
	private static TreeMap<String, Set<MetaDoc>> yearToMetaDocs = new TreeMap<String, Set<MetaDoc>>();
	private static Map<String, Integer> wordToID = new HashMap<String, Integer>();
	private static Set<String> malletDocs = new HashSet<String>();
	public static Map<String, Set<String>> authorToReports = new HashMap<String, Set<String>>();
	
	public static void main(String[] args) throws IOException {
		
		// loads just the list of mallet doc names
		loadMalletDocs(malletInputFile);
		
		// loads metadocs (for the docs in allDocuments) and fills in yearToMetaDocs, too
		// NOTE: i'm doing this first because otherwise, it's tedious to go through all reportToSources and remove those
		// which do not contain metadocs, so now, i just want store them if they don't have a metaDocs
		loadMetaDocs(metaDocsFile);
		
		// reads in the referential file to constructs the report->{source1, source2, ...}
		// until we have >= unique docs than 'maxDocsToCompriseCorpus'
		loadReferential(referentialFile);
		
		System.out.println("# reports: " + reportToSources.keySet().size());
		System.out.println("# total docs: " + allDocuments.size());
		
		allDocuments.clear();
		Set<String> trainingSources = new HashSet<String>();
		List<String> trainingLines = new ArrayList<String>();
		List<String> testingLines = new ArrayList<String>();
		
		// determines the training and test sets;
		// first, count how many non-singleton sources each report contains, which we'll then rank by
		Map<String, Integer> reportToNumMultiSources = new HashMap<String, Integer>();
		for (String report : reportToSources.keySet()) {
			int numNonSingles = 0;
			for (String source : reportToSources.get(report)) {
				if (sourceToReports.get(source).size() > 1) {
					numNonSingles++;
				}
			}
			reportToNumMultiSources.put(report, numNonSingles);
		}
		java.util.Iterator it = reportToSources.keySet().iterator(); // sortByValueDescending(reportToNumMultiSources).keySet().iterator();
		while (it.hasNext()) {
			String report = (String)it.next();
			System.out.println(report + " has " + reportToNumMultiSources.get(report));
			
			if (reportToNumMultiSources.get(report) < 6) {
				continue;
			}
			
			for (String source : reportToSources.get(report)) {
				//System.out.println(((double)trainingLines.size() / (double)(testingLines.size() + 0.001)));
				// source not in training yet, so let's not add it to test
				if (!trainingSources.contains(source)) {
					trainingLines.add(source + " " + report + "\n");
					trainingSources.add(source);
				} else {

					if (((double)trainingLines.size() / (double)(trainingLines.size() + testingLines.size() + 0.001)) < trainingPercentage) {
						trainingLines.add(source + " " + report + "\n");
						trainingSources.add(source);
					} else {
						testingLines.add(source + " " + report + "\n");
					}
				}
				
				allDocuments.add(report);
				allDocuments.add(source);
			}
			
			if (allDocuments.size() >= (double)maxDocsToCompriseCorpus) {
				break;
			}
		}
		
		System.out.println("all docs: " + allDocuments.size());
		// goes through the original mallet file in order to make:
		// - mallet output file (on the N subset of docs that are within allDocuments)
		// - .content file
		BufferedReader bin = new BufferedReader(new FileReader(malletInputFile));
		BufferedWriter bMallet = new BufferedWriter(new FileWriter(malletOutFile));
		BufferedWriter bContent = new BufferedWriter(new FileWriter(contentOutFile));
		
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();		
			
			// only process the doc if it's in our set
			if (!allDocuments.contains(docName)) {
				continue;
			}
			st.nextToken(); // pointless token

			Map<Integer, Integer> docWordCount = new HashMap<Integer, Integer>();

			while (st.hasMoreTokens()) {
				String curWord = st.nextToken();
				int wordID = 0;

				// gets a unique word ID for it
				if (wordToID.containsKey(curWord)) {
					wordID = wordToID.get(curWord);
				} else {
					wordID = wordToID.keySet().size();
					wordToID.put(curWord, wordID);
				}

				// updates our doc's word count
				if (docWordCount.containsKey(wordID)) {
					docWordCount.put(wordID, docWordCount.get(wordID)+1);
				} else {
					docWordCount.put(wordID, 1);
				}
			}
			
			// writes to the small mallet file
			bMallet.write(curLine + "\n");
			
			// writes to the .content file
			bContent.write(docName);
			for (int wordID : docWordCount.keySet()) {
				bContent.write(" " + wordID + " " + docWordCount.get(wordID));
			}
			bContent.write(" 0\n"); // writes pointless, but mandatory, 'label' i.e., topic id for PMTLM
		}
		bMallet.close();
		bContent.close();
		
		// writes .training and .testing
		BufferedWriter bTraining = new BufferedWriter(new FileWriter(trainingOutFile));
		BufferedWriter bTesting = new BufferedWriter(new FileWriter(testingOutFile));

		for (String l : trainingLines) {
			bTraining.write(l);
		}
		
		for (String l : testingLines) {
			bTesting.write(l);
		}
		/*
		int numLinks = 0;
		int numTraining = 0;
		Random rand = new Random();
		for (String report : reportToSources.keySet()) {
			for (String source : reportToSources.get(report)) {
				if (rand.nextDouble() < trainingPercentage) {
					bTraining.write(source + " " + report + "\n");
					numTraining++;
				} else {
					bTesting.write(source + " " + report + "\n");					
				}
				numLinks++;
			}
		}
		*/
		int numLinks = trainingLines.size() + testingLines.size();
		int numTraining = trainingLines.size();
		System.out.println("total links: " + numLinks);
		System.out.println("# training: " + numTraining + " (" + (double)numTraining/numLinks + ")");
		bTraining.close();
		bTesting.close();
	}

	// stores all mallet doc names, so that we're guaranteed to have the info later
	private static void loadMalletDocs(String mf) throws IOException {
		BufferedReader bin = new BufferedReader(new FileReader(malletInputFile));
		String curLine = "";
		while ((curLine = bin.readLine())!=null) {
			StringTokenizer st = new StringTokenizer(curLine);
			String docName = st.nextToken();
			malletDocs.add(docName);
		}
	}

	// creates an ordered list out of the passed-in set
	private static String[] createOrderedListFromSet(Set<String> docs) {
		String[] ret = new String[docs.size()];
		int i=0;
		for (String d : docs) {
			ret[i] = d;
			i++;
		}
		return ret;
	}

	// assumes this format:
	//	id = {D10-1001}
	//	author = {Rush, Alexander M.; Sontag, David; Collins, Michael John; Jaakkola, Tommi}
	//	title = {On Dual Decomposition and Linear Programming Relaxations for Natural Language Processing}
	//	venue = {EMNLP}
	//	year = {2010}
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
			
			if (!malletDocs.contains(docID)) {
				continue;
			}
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
						
						// updates auth -> <reports>
						Set<String> tmpReports = new HashSet<String>();
						if (authorToReports.containsKey(authorToken)) {
							tmpReports = authorToReports.get(authorToken);
						}
						tmpReports.add(docID);
						authorToReports.put(authorToken, tmpReports);
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
			
			// updates the year -> <MetaDocs> map
			Set<MetaDoc> tmpMetadocs = new HashSet<MetaDoc>();
			if (yearToMetaDocs.containsKey(year)) {
				tmpMetadocs = yearToMetaDocs.get(year);
			}
			tmpMetadocs.add(md);
			yearToMetaDocs.put(year, tmpMetadocs);
		}
		
	}

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
			/*
			while (it.hasNext() && i <= r) {
				chosenAuthor = (String)it.next();
				i++;
			}
			*/
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
		/*
		java.util.Iterator<String> it = yearToMetaDocs.descendingKeySet().iterator();
		int numReports = 0;
		while (it.hasNext()) {
			String year = (String)it.next();
			System.out.println(year + " has " + yearToMetaDocs.get(year).size() + " metadocs");
			for (MetaDoc md : yearToMetaDocs.get(year)) {
				String report = md.docID;
				if (allReportToSources.containsKey(report)) {
					allDocuments.add(report);
					
					numReports++;
					for (String source : allReportToSources.get(report)) {
						allDocuments.add(source);
					}
					
					reportToSources.put(report,  allReportToSources.get(report));
					
					// checks if we've met our max for # of docs in our to-be corpus
					if (allDocuments.size() >= maxDocsToCompriseCorpus) {
						System.out.println("numReportS: " + numReports);
						return;
					}
				}
			}
		}
		*/
		System.out.println("finished; numReportS: " + numReports);
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
		for (java.util.Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
}

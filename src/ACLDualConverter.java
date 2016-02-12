import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
import java.util.Set;
import java.util.StringTokenizer;


public class ACLDualConverter {

	// NOTE: i am not removing: ; . ! ? like i originally was doing because now i want
	// to preserve sentences as they are delimited
	// ";", "\\.", "\\?", "\\!",
	private static List<String> badCharacters = new ArrayList<String>(Arrays.asList("\\(see", "@", "\\.", "\\/", "\\?", "\\%", "above\\.\\)", "\\.\\)", "“", "_", " ", "…", "”", "‘", "’", ",", "\"", "–", "\\(", "\\)", "\\[", "\\]", ":", "-", "<p>", "\\|", "\\+", "<\\/p>", "\\n", "<\\\\p>"));
	private static List<String> badSpaces = new ArrayList<String>(Arrays.asList("http", "www")); 
	private static List<String> endPunctuation = new ArrayList<String>(Arrays.asList(".", "!", "?"));
	
	private static Map<String, MetaDoc> docIDToMeta = new HashMap<String, MetaDoc>();
	private static Map<String, Set<String>> reportToSources = new HashMap<String, Set<String>>();
	
	public static int maxParenSize = 70; // max size of the cited block ( ); if longer than this, clear all '(' positions
	public static int minSentenceCharLength = 40;
	public static int minNumWordsPerCleanedSentence = 7;
	public static int minNumWordsInDoc = 500;
	public static int maxNumWordsInDoc = 9999;
	public static int minNumMalletDocTokens = 100;
	public static boolean requireAbstract = false;
	public static int maxNumDocs = 99999;
	public static int minNumSources = 0; // doubles as being both (1) min # of citations matched for a given report; (2) min # of valid (i.e., meets min # of words) sources
	public static double minPercCitationsMatched = 0.0; // was 0.9
	public static int minNumSections = 0;
	
	// stores our stopwords and docs which we only care about (if 'useAllDocs = false')
	public static Set<String> stopwords = new HashSet<String>();
	public static Set<String> goodDocs = new HashSet<String>();
	
	// input
	public static String aclNetworkFile = "/Users/christanner/research/data/aan/release/2013/acl.txt";
	public static String aclMetaFile = "/Users/christanner/research/data/aan/release/2013/acl-metadata.txt";
	public static String aclDir = "/Users/christanner/research/data/aan/papers_text/";
	public static String stopwordsFile = "/Users/christanner/research/data/aan/stopwords.txt";
	public static String doclistFile = "/Users/christanner/research/data/aan/doclist2.txt"; // in case we want to run on just a few docs, instead of all
	
	// output
	public static String outputDir = "/Users/christanner/research/data/aan/citation_prediction/"; // was dualformat as the last subdir
	public static String docLegendFile = "/Users/christanner/research/projects/CitationFinder/input/all/docsLegend.txt"; //"/Users/christanner/research/data/aan/docLegendFile.txt"; 
	public static String corpusStatsFile = "/Users/christanner/research/projects/CitationFinder/input/all/corpus_stats.txt";
	public static String referentialOutput = "/Users/christanner/research/projects/CitationFinder/input/all/referential.txt";
	public static String malletFile = "/Users/christanner/research/projects/CitationFinder/input/all/mallet-input.txt";
	
	public static final boolean useAllDocs = true; // if false, we will only load the docs in the file above
	
	public static Map<String, ACLDocument> aclDocs = new HashMap<String, ACLDocument>();
	
	// for making input for our regular LDA-system, in case we want to use it
	//public static String rawReportsOutputDir = "/Users/christanner/research/data/aan/rawReports_1000/";
	//public static String annoReportsOutputDir = "/Users/christanner/research/data/aan/annoReports_1000/";
	//public static String sourcesOutputDir = "/Users/christanner/research/data/aan/sources_1000/";

	public static void main(String[] args) throws IOException {
		
		// loads stopwords
		stopwords = loadList(stopwordsFile);
		
		// loads doclist
		Set<String> goodDocs = new HashSet<String>();
		goodDocs = loadList(doclistFile);
		
		// reads through the ACL network file and constructs the report->{source1, source2, ...}
		reportToSources = new HashMap<String, Set<String>>();
		
		// stores a list of all docs (reports and sources), without the .txt
		Set<String> allDocuments = new HashSet<String>();
		
		// stores each doc's name and if it's valid or not (valid = has abstract and >= minNumwords)
		Map<String, Boolean> docToValid = new HashMap<String, Boolean>();
		
		BufferedReader bin = new BufferedReader(new FileReader(aclNetworkFile));
		BufferedWriter bout = null;
		
		String curLine = "";
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
			if (reportToSources.containsKey(report)) {
				curSources = reportToSources.get(report);
			}
			if (!report.equals(source)) {
				curSources.add(source);
			}
			reportToSources.put(report, curSources);
			
			// updates our set of all docs
			if (useAllDocs) {
				allDocuments.add(report);
				allDocuments.add(source);
			} else { // only load the docs that are in our 'goodDocs' list
				if (goodDocs.contains(report)) {
					allDocuments.add(report);
				}
				if (goodDocs.contains(source)) {
					allDocuments.add(source);
				}
			}
		}
		
		System.out.println("# unique docs before pruning: " + allDocuments.size());
		
		// loads the metaDocs
		docIDToMeta = new HashMap<String, MetaDoc>();
		bin = new BufferedReader(new FileReader(aclMetaFile));
		curLine = "";
		
		// assumes this format:
//		id = {D10-1001}
//		author = {Rush, Alexander M.; Sontag, David; Collins, Michael John; Jaakkola, Tommi}
//		title = {On Dual Decomposition and Linear Programming Relaxations for Natural Language Processing}
//		venue = {EMNLP}
//		year = {2010}
//
//		id = {D10-1002}
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
			
			// author line
			//curLine = bin.readLine();
			//System.out.println("nextline:" + curLine);
			//st = new StringTokenizer(curLine);
			//st.nextToken();
			//st.nextToken();
			String authorLine = bin.readLine().toLowerCase(); //st.nextToken();
			authorLine = authorLine.substring(authorLine.indexOf("{")+1).replace("}", "");
			
			//authorLine = authorLine.replace("{", "").replace("}", "");
			//System.out.println("authorline:" + authorLine);
			st = new StringTokenizer(authorLine, ",;. ");
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
			//System.out.println("tokens found:" + authorTokens);
			
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
		}
		
		// now we have both the:
		// (1) network citation mappings
		// (2) metadocs for every doc
		// BUT some items in (1) may not be listed in (2)!! due to the creators of the ACL corpus
		// THUS, we need to eliminate the docs which we don't have (2) metadocs for.
		Set<String> reportsToRemove = new HashSet<String>();
		for (String report : reportToSources.keySet()) {
			if (!allDocuments.contains(report)) {
				System.err.println("*** I'm confused; why is this report not in allDocuments{} ?");
			}
			if (!docIDToMeta.containsKey(report) || !allDocuments.contains(report)) {
				reportsToRemove.add(report);
			}
		}
		System.out.println("removing " + reportsToRemove.size());
		for (String report : reportsToRemove) {
			reportToSources.remove(report);
			System.out.println("removing report:" + report);
		}
		reportsToRemove.clear(); // frees up memory
		
		for (String report : reportToSources.keySet()) {
			//System.out.print("report:" + report + "; removing:");
			Set<String> existingSources = new HashSet<String>();
			for (String source : reportToSources.get(report)) {
				if (docIDToMeta.containsKey(source)) {
					existingSources.add(source);
				} else {
					System.out.println("report: " + report  + " is missing: " + source);
				}
			}
			//System.out.println("");
			reportToSources.put(report, existingSources);
		}
		
		int tmpSources = 0;
		int tmpTotal = 0;
		for (String doc : allDocuments) {
			
			if (!reportToSources.containsKey(doc)) {
				tmpSources++;
			}
			tmpTotal++;
		}
		System.out.println("# sources:" + tmpSources + "; total:" + tmpTotal);
		
		//System.out.println("n04:" + reportToSources.get("N04-1030"));
		// so now let's parse every document
		int numValidDocs = 0;
		Map<String, ACLDocument> docNameToDoc = new HashMap<String, ACLDocument>();
		Set<String> nullDocs = new HashSet<String>();
		for (String doc : allDocuments) {
			
			if (!docIDToMeta.containsKey(doc)) {
				System.out.println("didn't have metadoc for it!");
				docToValid.put(doc, false);
				continue;
			}
			
			boolean isReport = false;
			if (reportToSources.containsKey(doc)) {
				isReport = true;
			}
			ACLDocument d = parseACLDocument(isReport, doc, aclDir + doc + ".txt");
			//System.out.println("doc has # sentences:" + d.contentPerSentence.size());
			//writeACLDocument(d, outputDir + doc + ".txt");
			
			if (d == null) {
				System.out.println("*** d was null, meaning file does not exist on hard disk");
				nullDocs.add(doc);
				docToValid.put(doc, false);
				continue;
			}
			
			//System.out.println(d);
			 
			// ensures doc is good thus far (later we also need to check that the sources themselves are actually legit, too)
			// NOTE: these are the requires for ANY doc (report or source); later we enforce stricter rules for reports (i.e., # of valid sources and # of sections)
			if (d.numWords >= minNumWordsInDoc) {
				docNameToDoc.put(doc, d);
				docToValid.put(doc, true);
				aclDocs.put(doc, d); // saves the ACLDocument (contains content, sentence information, etc)
				numValidDocs++;
			} else {
				docToValid.put(doc, false);
				
				// removes our report doc's map to sources
				System.out.println("we are removing: " + doc);
				reportToSources.remove(doc);
			}
		}
		
		for (String nullDoc : nullDocs) {
			allDocuments.remove(nullDoc);
			reportToSources.remove(nullDoc);
		}
		
		System.out.println("\n\tnum valid: " + numValidDocs);
		//d.percentageCitationsMatched >= minPercCitationsMatched && d.numCitationsMatched >= minNumSources) {
		Set<String> badReports = new HashSet<String>();
		for (String report : reportToSources.keySet()) {
			
			Set<String> newSources = new HashSet<String>();
			// only add the Sources which are 'valid' (i.e., > minNumWords and optionally requires Abstract
			for (String curSource : reportToSources.get(report)) {
				if (docToValid.containsKey(curSource) && docToValid.get(curSource) == true) {
					newSources.add(curSource);
				}
			}
			
			ACLDocument d = aclDocs.get(report);
			d.updateSources(newSources);
			aclDocs.put(report, d); // updates our hashmap, although maybe this isn't even necessary (java pass-by-reference?)
			
			if (d.percentageCitationsMatched < minPercCitationsMatched || d.numCitationsMatched < minNumSources || d.numSections < minNumSections) {
				badReports.add(report);
			} else {
				// sets our new report -> <sources> map
				reportToSources.put(report, newSources);
			}
		}

		// remove the reports which have only a few sources
		// and change the ACLDoc to be a source, if it still exists
		for (String doc : badReports) {
			reportToSources.remove(doc);
			aclDocs.get(doc).isReport = false;
		}
		
		Set<String> corpus = new HashSet<String>();
		Set<String> reportNames = new HashSet<String>();
		Set<String> sourceNames = new HashSet<String>();
		for (String report : reportToSources.keySet()) {
			if (corpus.size() < maxNumDocs) {
				corpus.add(report);
				reportNames.add(report);
				for (String source : reportToSources.get(report)) {
					corpus.add(source);
					sourceNames.add(source);
				}
			}
		}
		
		/*
		int uid = 0;
		Map<Integer, String> idToDocName = new HashMap<Integer, String>();
		Map<String, Integer> docNameToID = new HashMap<String, Integer>();
		
		for (String doc : corpus) {
			idToDocName.put(uid, doc);
			docNameToID.put(doc, uid);
			uid++;
		}
		*/
		
		System.out.println("reports: (" + reportNames.size() + "): " + reportNames);
		
		// writing doclegends and referential was here
		
		/*
		// writes each Report file to the harddrive
		for (String report : reportNames) {
			
			int id = docNameToID.get(report);
			writeTRECDoc(rawReportsOutputDir + id + ".txt", String.valueOf(id), aclDocs.get(report).pureContent);
			writeTRECDoc(annoReportsOutputDir + id + ".txt", String.valueOf(id), aclDocs.get(report).pureContent);
		}
		
		// writes each Source file to the harddrive
		for (String source : sourceNames) {
				writeTRECDoc(sourcesOutputDir + source + ".txt", source, aclDocs.get(source).pureContent);
		}
		*/
		
		// every report word must be contained within a source, too.
		// oh, but we have to make sure not to double count (some docs can be both reports and sources,
		// so we should check that a source really is a source other than the given report.  thus,
		// we need to actually keep track of word -> <source doc17, source doc38, ... >
		Map<String, Set<String>> wordToSources = new HashMap<String, Set<String>>();
		for (String source : sourceNames) {
			Set<String> uniqueWords = getUniqueWords(aclDocs.get(source));
			
			for (String word : uniqueWords) {
				
				// updates our set of sources which contain the given word
				Set<String> curSources = new HashSet<String>();	
				if (wordToSources.containsKey(word)) {
					curSources = wordToSources.get(word);
				}
				curSources.add(source);
				wordToSources.put(word, curSources);
			}
		}
		
		// do the same for the reports
		Map<String, Set<String>> wordToReports = new HashMap<String, Set<String>>();
		for (String report : reportNames) {
			Set<String> uniqueWords = getUniqueWords(aclDocs.get(report));
			
			for (String word : uniqueWords) {
				
				// updates our set of sources which contain the given word
				Set<String> curReports = new HashSet<String>();	
				if (wordToReports.containsKey(word)) {
					curReports = wordToReports.get(word);
				}
				curReports.add(report);
				wordToReports.put(word, curReports);
			}
		}
		
		Set<String> validWords = new HashSet<String>();
		// makes a list of words that appear in both reports and sources
		for (String word : wordToReports.keySet()) {
			Set<String> foundReports = wordToReports.get(word);
			
			if (wordToSources.containsKey(word)) {
				Set<String> foundSources = wordToSources.get(word);
				
				for (String eachReport : foundReports) {
					foundSources.remove(eachReport);
				}
				
				// if we have any remaining source, it means the source isn't a report, and thus the word
				// appears in both a distinct report and source which aren't the same doc
				if (foundSources.size() > 0) {
					validWords.add(word);
				}
				
			}
		}
		
		System.out.println("# of unique words in reports: " + wordToReports.keySet().size());
		System.out.println("# of unique words in sources: " + wordToSources.keySet().size());
		System.out.println("# of unique words in both, disjointly: " + validWords.size());
		
		// updates the docs to contain a valid-words version of every sentence, along with the total content
		for (String doc : corpus) {
			aclDocs.get(doc).updateContent(validWords, stopwords, minNumWordsPerCleanedSentence);
		}
		
		// writes the mallet-input-file
		bout = new BufferedWriter(new FileWriter(malletFile));
		int numReportsAndSources = 0;
		Set<String> badMalletFiles = new HashSet<String>();
		
		for (String doc : corpus) {	

			/*
			String content = aclDocs.get(doc).pureContent;
			String filteredContent = "";
			StringTokenizer st = new StringTokenizer(content);
			while (st.hasMoreTokens()) {
				String w = (String)st.nextToken();
				if (validWords.contains(w)) {
					filteredContent += w + " ";
				}
			}
			filteredContent = filteredContent.trim(); // removes the trailing space
			*/
			
			//int id = docNameToID.get(doc);
			String pureContent = aclDocs.get(doc).pureContent;
			StringTokenizer st = new StringTokenizer(pureContent);
			if (st.countTokens() >= minNumMalletDocTokens) {
				bout.write(doc + " " + doc + " " + pureContent + "\n");
				if (reportNames.contains(doc) && sourceNames.contains(doc)) {
					numReportsAndSources++;
				}				
			} else {
				badMalletFiles.add(doc);
			}
		}
		bout.close();
		
		System.out.println("out of the " + corpus.size() + ", " + numReportsAndSources + " were both reports and sources");
		System.out.println("and " + reportNames.size() + " were reports");
		System.out.println("# of bad mallet files: " + badMalletFiles.size() + " = " + badMalletFiles);
		
		// makes the doc file (aka docID file)
		bout = new BufferedWriter(new FileWriter(docLegendFile));
		bout.write("size of corpus: " + corpus.size() + "\n\n");
		bout.write("filename\treport\tsource:\n");
		//for (int i=0; i<uid; i++) {
		for (String docName : corpus) {
			
			if (badMalletFiles.contains(docName)) {
				continue;
			}
			
			//String docName = idToDocName.get(i);
			bout.write(docName + "\t");

			if (reportNames.contains(docName)) {
				bout.write("*\t");
			} else {
				bout.write(" \t");
			}
			if (sourceNames.contains(docName)) {
				bout.write("*\n");
			} else {
				bout.write("\n");
			}
		}
		bout.close();
		
		// writes the referential file
		bout = new BufferedWriter(new FileWriter(referentialOutput));
		//for (Integer id : reportIDs) {
		for (String report : reportNames) {
			
			//int id = docNameToID.get(report);
			
			// iterates over every Source for the given Report
			//System.out.println(report + "'s sources: " + reportToSources.get(report));
			if (badMalletFiles.contains(report)) {
				continue;
			}
			for (String source : reportToSources.get(report)) {
				
				if (corpus.contains(source) && !badMalletFiles.contains(source)) {
					//bout.write(id + " $RAWREPORTS$" + id + ".txt $ANNOREPORTS$" + id + ".txt $SOURCES$" + docNameToID.get(source) + ".txt\n");
					bout.write(report + " " + source + "\n");
				}
			}
		}
		bout.close();
		
		// writes each dual-formatted file to hard disk (i.e., 1 sentence per line for a given report)
		for (String doc : corpus) {
			writeACLDocument(validWords, aclDocs.get(doc), outputDir + doc + ".txt");
		}
		
		
		
		// writes how many times each report was also cited by other reports
		// (just for eugene's experiment of looking at a popular paper)
		// this is just an approximate popularity check amongst our reports
		bout = new BufferedWriter(new FileWriter(corpusStatsFile));
		Map<String, Integer> reportCitationCounts = new HashMap<String, Integer>();
		for (String report : reportNames) {
			if (sourceNames.contains(report)) {
				// count how many reports cite it
				int numCitations = 0;
				for (String report2 : reportNames) {
					ACLDocument tmp = aclDocs.get(report2);
					// checks if the given (other) report cites the given report #1
					if (tmp.citationToMatched.containsKey(report) && tmp.citationToMatched.get(report)) {
						numCitations++;
					}
				}
				reportCitationCounts.put(report, numCitations);
			}
		}
		Iterator it = sortByValueDescending(reportCitationCounts).keySet().iterator();
		bout.write("report,# times cited by other reports, paper by eugene?\n");
		while (it.hasNext()) {
			String doc = (String)it.next();
			boolean fromEugene = false;
			MetaDoc md = docIDToMeta.get(doc);
			if (md.names.contains("eugene") || md.names.contains("charniak")) {
				bout.write(doc + ", " + reportCitationCounts.get(doc) + ",true\n");
			} else {
				bout.write(doc + ", " + reportCitationCounts.get(doc) + "\n");
			}
		}
		bout.write("\neugene's reports:\n");
		for (String doc : reportNames) {
			MetaDoc md = docIDToMeta.get(doc);
			if (md.names.contains("charniak")) {
				bout.write(doc + "\n");
			}
		}
		bout.close();
	}
	
	// returns the unique set of words that appears in the passed-in doc
	private static Set<String> getUniqueWords(ACLDocument d) {
		Set<String> ret = new HashSet<String>();
		StringTokenizer st = new StringTokenizer(d.pureContent);
		while (st.hasMoreTokens()) {
			String w = (String)st.nextToken();
			ret.add(w);
		}
		return ret;
	}

	// writes the passed-in string/content to the passed-in file location as a TREC doc
	// writes the new TREC-formatted file
	private static void writeTRECDoc(String outputFile, String docNo, String content) throws IOException {
		BufferedWriter bout = new BufferedWriter(new FileWriter(outputFile));
		bout.write("<DOC>\n");
		bout.write("<DOCNO>" + docNo + "</DOCNO>\n");
		bout.write("<url>0</url>\n");
		bout.write("<HEADLINE>\n");
		bout.write("</HEADLINE>\n");
		bout.write("<TEXT>\n");
		bout.write("<P>\n");
		bout.write(content + "\n");
		bout.write("</P>\n");
		bout.write("</TEXT>\n");
		bout.write("</DOC>\n");
		bout.close();
	}
	
	private static void writeACLDocument(Set<String> validWords, ACLDocument d, String outputFile) throws IOException {
		BufferedWriter bout = new BufferedWriter(new FileWriter(outputFile));
		
		// if report, let's write each sentence,
		if (d.isReport) {
		// for each sentence, writes (1) the source's docIDs and (2) the sentence's content
			for (int i=0; i<d.rawContentPerSentence.size(); i++) {
				/*
				String content = d.rawContentPerSentence.get(i);
				String filteredContent = "";
				StringTokenizer st = new StringTokenizer(content);
				while (st.hasMoreTokens()) {
					String w = (String)st.nextToken();
					if (validWords.contains(w)) {
						filteredContent += w + " ";
					}
				}
				filteredContent = filteredContent.trim(); // removes the trailing space
				*/
				bout.write(d.citedListPerSentence.get(i) + "\t" + d.sectionNumPerSentence.get(i) + "\t" + 
						d.sectionNamePerSentence.get(i) + "\t" + d.rawContentPerSentence.get(i) + "\t" + 
						d.cleanedContentPerSentence.get(i) + "\n");
			}
		} else { // represents we are just a source, so let's just output the content/text
			/*
			String content = d.pureContent;
			String filteredContent = "";
			StringTokenizer st = new StringTokenizer(content);
			while (st.hasMoreTokens()) {
				String w = (String)st.nextToken();
				if (validWords.contains(w)) {
					filteredContent += w + " ";
				}
			}
			filteredContent = filteredContent.trim(); // removes the trailing space
			*/
			bout.write(d.pureContent);
		}
		bout.close();
		
		/*
		bout.write("\n\neach citation:\n");
		for (String source : d.citationToMatched.keySet()) {
			bout.write(source + " = " + d.citationToMatched.get(source) + "\n");
			if (d.citationToMatched.get(source) == false) {
				bout.write("\t" + docIDToMeta.get(source) + "\n");
			}
		}
		
		bout.write("\n\neach ( ) found:\n");
		for (String paren : d.parenToMetaDocList.keySet()) {
			bout.write(paren + " => " + d.parenToMetaDocList.get(paren) + "\n");
		}
		bout.close();
		*/
	}

	// parses a document from the ACL corpus
	// the content is what's between the 'Abstract' and 'References' tags
	private static ACLDocument parseACLDocument(boolean isReport, String docID, String docFile) throws IOException {
		
		//System.out.println("parseACLDocment:" + docFile);
		//System.out.println("has sources: " + reportToSources.get(docID).size());
		
		Set<String> tmpSources = reportToSources.get(docID);
		// iterates over all cited Sources
		/*
		for (String sourceID : tmpSources) {
			System.out.println(docIDToMeta.get(sourceID));
		}
		*/
		ACLDocument d = null;

		// if the doc doesn't exist on the hard drive, return ""
		File f = new File(docFile);
		if(f.exists()) {
				
			// following 2 data structures rely on the ordering;
			// item 0 corresponds to the 1st sentence
			// item 1 corresponds to the 2nd sentence, etc
			List<Set<String>> citedListPerSentence = new ArrayList<Set<String>>();
			List<String> contentPerSentence = new ArrayList<String>();
			List<Integer> sectionNumPerSentence = new ArrayList<Integer>();
			List<String> sectionNamePerSentence = new ArrayList<String>();
			//List<Integer> changeLineNums = new ArrayList<Integer>();
			//List<String> sectionNames = new ArrayList<String>();
			
			Integer curSectionNum = 0;
			String curSectionName = "";
			// stores 'true' or 'false' for every citation that is supposed to be found for the current doc
			Map<String, Boolean> citationToMatched = new HashMap<String, Boolean>();
			if (isReport) {
				for (String citedDocID : reportToSources.get(docID)) {
					citationToMatched.put(citedDocID, false);
				}
			}
			// stores year -> <sentence #, sentence #>
			Map<String, Set<Integer>> yearToSentenceNumber = new HashMap<String, Set<Integer>>();
			
			// stores every ( ) which we think is a citation, mapped to the MetaDoc we think it cites (or NULL if none)
			// ASSUMES we never see the exact same ( ) citation;
			// NOTE: this data structure is only for debugging purposes anyway, to ensure we're doing the right stuff.
			//       ultimately, it will get stored in the above to 'list' data structures anyway
			Map<String, List<MetaDoc>> parenToMetaDocList = new HashMap<String, List<MetaDoc>>();
			
			// make it all 1 line, while:
			// - flag and only look at content between abstract and acknowledgments/references
			// - appending lines that end in -
			BufferedReader bin = new BufferedReader(new FileReader(docFile));
			String rawContent = "";
			String curLine = "";
			String curSentence = "";
			Set<String> curSetPerSentence = new HashSet<String>();
			
			boolean lastLineHadHyphen = false;
			boolean foundIntroduction = false;
			boolean foundAbstract = false;
			int lineNum = 0;
			while ((curLine = bin.readLine()) != null) {
				curLine = curLine.toLowerCase().trim();
				
				// checks if we have a new section name (ignores subsections)
				StringTokenizer tmpLine = new StringTokenizer(curLine);
				if (tmpLine.countTokens() > 1 && tmpLine.countTokens() < 9) {
					String firstToken = tmpLine.nextToken();
					// line starts with a new section #
					
					//System.out.println("looking for section" + String.valueOf(Integer.toString(curSectionNum+1)) + "; curline:" + curLine);
					
					if (firstToken.length() <= 2 && firstToken.startsWith(String.valueOf(Integer.toString(curSectionNum+1)))){
						//System.out.println("inside:" + firstToken);
						// ensures rest is a title (by ensuring < 9 tokens, all being non-numerics)
						boolean containsDigit = false;
						String sectionName = "";
						while (tmpLine.hasMoreTokens() && !containsDigit) {
							String token = tmpLine.nextToken();
							sectionName += token + "_";
							for (int j=0; j<token.length(); j++) {
								if (Character.isDigit(token.charAt(j))) {
									containsDigit = true;
									break;
								}
							}
						}
						if (!containsDigit) {
							sectionName = sectionName.trim();
							sectionName = sectionName.substring(0, sectionName.length()-1);
							curSectionNum++;
							rawContent = rawContent + " AAAAA_" + curSectionNum + "_" + sectionName + " ";
							//System.out.println("we just found new sections: " + curSectionNum); //; citedlistsize:" + citedListPerSentence.size() + "; change line nums now:" + changeLineNums);
							continue;
						}
					}
				}
				
				if (curLine.equals("")) { continue;}
				//System.out.println("curline:" + curLine);
				if (lineNum < 40 && !foundIntroduction && !foundAbstract && (curLine.indexOf("abstract") != -1 || curLine.indexOf("abs") == 0) && curLine.length() <= 17) {
					foundAbstract = true;
					rawContent = "";
					lastLineHadHyphen = false;
					foundIntroduction = false;
					
					
					rawContent = "AAAAA_0_abstract ";
					continue;
				}
				

				
				// let's disregard the content before the introduction
				if (lineNum < 60 && !foundIntroduction && curLine.indexOf("introduction") != -1 && curLine.length() <= 20) {
					
					// if we have no guarantee that we've seen a section title before (i.e., abstract),
					// we should clear out our content now, as it probably contains email header info
					if (!foundAbstract) {
						rawContent = "AAAAA_1_introduction ";//introduction ";
					}
					lastLineHadHyphen = false;
					foundIntroduction = true;
					continue;
					
				// we reached the end of content that we care about
				} else if (curLine.equals("acknowledgments") || curLine.equals("references")) {
					break;
				}
				
				// if we didn't have a hyphen on the last line, let's add a space at the beginning
				if (!lastLineHadHyphen) {
					curLine = " " + curLine;
				}
				
				// checks if we end in an '-'
				if (curLine.lastIndexOf('-') == curLine.length()-1) {
					//System.out.println("curline:" + curLine);
					curLine = curLine.substring(0, curLine.length()-1);
					lastLineHadHyphen = true;
				} else {
					lastLineHadHyphen = false;
				}
				
				// adds to the main 'content'
				rawContent = rawContent + curLine;
				lineNum++;
			}
			rawContent = rawContent.trim();
			
			// removes all words which start with 'bad' sequences
			Set<String> badTokens = new HashSet<String>();
			StringTokenizer tmp = new StringTokenizer(rawContent);
			while (tmp.hasMoreTokens()) {
				String token = tmp.nextToken();
				for (String badStart : badSpaces) {
					if (token.indexOf(badStart) != -1) {
						badTokens.add(token);
					}
				}
			}
			/*
			if (badTokens.size() > 0) {
				System.out.println("*** bad tokens:" + badTokens);
			}
			*/
			//System.out.println("raw length:" + rawContent.length());
			for (String badToken : badTokens) {
				while (rawContent.indexOf(badToken) != -1) {
					rawContent = rawContent.replace(badToken, " ");
				}
				rawContent = rawContent.replace(";", " ; ");
				rawContent = rawContent.replaceAll(";", " ; ");
			}
			
			if (!isReport) {
				rawContent = rawContent.trim();
				for (String badChar : badCharacters) {
					rawContent = rawContent.replaceAll(badChar, " ");
				}
				StringTokenizer st = new StringTokenizer(rawContent);
				String ret = "";
				int numTokensUsed = 0;
				
				// only if we meet our requirements do we try to fill in a non-empty 'ret' content string
				// (1) > minNumWords
				// (2) optionally requires 'abstract'
				if (st.countTokens() >= minNumWordsInDoc && !(requireAbstract && !foundAbstract)) {
					while (st.hasMoreTokens() && numTokensUsed < maxNumWordsInDoc) {
						
						String token = st.nextToken();
						if (token.length() > 1) {// && !stopwords.contains(token)) {
							
							boolean isDigit = true;
							for (int j=0; j<token.length(); j++) {
								if (!Character.isDigit(token.charAt(j))) {
									isDigit = false;
									break;
								}
							}
							
							if (!isDigit) {
								ret += " " + token;
								numTokensUsed++;
								
								if (numTokensUsed >= maxNumWordsInDoc) {
									break;
								}
							}
						}
					}
					ret = ret.trim();
				}
				d = new ACLDocument(ret);
				return d;
			} // end of checking if we are just a source
			
			//System.out.println("raw length:" + rawContent.length());
			//System.out.println("raw content now:" + rawContent);
			// step through each char, just like originally,
			// and try to key on the year first.
			// if it's 2 digits, make it 4 (if it's  > 14, make it 19+yr, else 20+yr)
			// then, for every metadoc that's listed as a citation for the given doc,
			// keep those as candidates which we'll try to match the ( )'s authors against.
			// they'll probably be some citations that have multiple matches
			// like 'klein 2000' could cite 2 papers from the same yr?  so i should do counts...
			// each metadoc gets a count.  the metadoc with the most author-hit-counts (which also match on the yr) wins
			// ALSO separate multiple citations within a ( )... so look at examples to see what the delimeters are (;?)
			
			// for every citation ( ) bracket, keep a list of the matches... it could INCLUDE nulls too.
			// like (Klein 2000; Morris 1998; Halbert 2008) -> <MetaDoc17, null, MetaDoc81> if the 2nd one didn't match... or single ones
			// like (Klein 2000) -> <MetaDoc17>
			
			// when i see a punctuation, chk if (1) bracketCount == 0 && (2) the current sentence is > minSentenceLength (make this global var... i think it should be 30 or so)
			// if it is, then end the sentence, ignore the . and clear stuff (like the current sentence list of stuff... and add those to the current sentence list ones)
			// steps through each character, looking for matching ( ), which we will remove if it's
			// shorter than 60 characters and contains a #
			List<Integer> startingParens = new ArrayList<Integer>();
			curSectionNum = 0; // RESETS SECTION NUM
			
			//System.out.println("rawContent's 1st index of . is:" + rawContent.indexOf("."));
			for (int i=0; i<rawContent.length(); i++) {
				
				// checks if we have a looong ( ), in which case it might be the case the ones outside of it are even longer,
				// so let's just clear all paren counts, as something is probably off
				if (startingParens.size() > 0 && startingParens.get(startingParens.size()-1) + maxParenSize < i) {
					startingParens.clear();
				}
				
				// checks if we've reached a . or !
				// AND we are not in a ( )
				// AND we have met the min. length for a sentence that we wish to save
				if (endPunctuation.contains(String.valueOf(rawContent.charAt(i)))) {
					
					//System.out.println("*** found punctuation");
					// i put this if as its own because i want to use the bottom 'else' to catch non-end-punctuation...
					// not just things that don't match all 3 criteria
					if (startingParens.size() == 0 && curSentence.length() > minSentenceCharLength) {
						
						String cleanedSentence = "";
						// looks for year and cleans up tokens
						StringTokenizer st2 = new StringTokenizer(curSentence);
						while (st2.hasMoreTokens()) {
							String token = st2.nextToken();
							
							// checks if we have a new curSectionNum or sectionName
							if (token.startsWith("AAAAA_")) {
								StringTokenizer tmpA = new StringTokenizer(token, "_");
								if (tmpA.countTokens() >= 3) {
									tmpA.nextToken();
									curSectionNum = Integer.parseInt(tmpA.nextToken());
									curSectionName = "";
									while (tmpA.hasMoreTokens()) {
										curSectionName += tmpA.nextToken() + " ";
									}
									curSectionName = curSectionName.trim();
									continue;
								}
							}
							cleanedSentence += token + " ";
							String year = "";
							boolean isDigit = true;
							for (int j=0; j<token.length(); j++) {
								if (!Character.isDigit(token.charAt(j))) {
									isDigit = false;
									break;
								}
							}
							
							if (isDigit) {
								// if year is 2 digits and after 1960
								if (token.length() == 2 && Integer.parseInt(token) > 60) {
									year = "19" + token;
								} else if (token.length() == 2 && Integer.parseInt(token) < 15) {
									year = "20" + token;
								} else if (token.length() == 4 && ((Integer.parseInt(token) > 1960) || Integer.parseInt(token) < 2015)) {
									year = token;
								}
							}
							
							if (!year.equals("")) {
								Set<Integer> tmpSentNums = new HashSet<Integer>();
								if (yearToSentenceNumber.containsKey(year)) {
									tmpSentNums = yearToSentenceNumber.get(year);
								}
								tmpSentNums.add(contentPerSentence.size());
								yearToSentenceNumber.put(year,  tmpSentNums);	
							}
						}
						
						cleanedSentence = cleanedSentence.trim();
						

						// saves away our currentSentence AND its doc IDs
						contentPerSentence.add(cleanedSentence);
						Set<String> newCopy = new HashSet<String>(curSetPerSentence);
						citedListPerSentence.add(newCopy);
						//System.out.println("WE ARE UPDATING citedListPerSentence and now have a size:" + citedListPerSentence.size() + " = " + cleanedSentence);
						curSentence = "";
						curSetPerSentence.clear();
						
						
						// adds the section num and name
						// checks for a new curSectionNum and sectionName
						sectionNumPerSentence.add(curSectionNum);
						sectionNamePerSentence.add(curSectionName);
						
						//String tmp1 = Integer.toString(curSectionNum);
						//Integer tmp2 = Integer.valueOf(tmp1);
						//int tmp3 = Integer.valueOf(Object.clone())). //valueOf(curSectionNum);
						//String tmp2 = String.valueOf(curSectionName);
						//sectionNumPerSentence.add(curSectionNum);
						//sectionNamePerSentence.add(curSectionName);
						
					}
				} else if (rawContent.charAt(i) == '(') {
					startingParens.add(i);
				} else if (rawContent.charAt(i) == ')') {

					if (startingParens.size() > 0) {
						int startingPos = startingParens.get(startingParens.size()-1);
						
						// ensures the ( ) is small enough for our caring
						//System.out.println("found a ), and it's:" + (i-startingPos) + " away");
						if (i - startingPos < maxParenSize) {
							String textBefore = rawContent.substring(0, startingPos);
							String textInBetween = rawContent.substring(startingPos+1, i);
							String textAfter = rawContent.substring(i+1);
							//System.out.println("textbefore:" + textBefore);
							//System.out.println("textinbetween:" + textInBetween);
							//System.out.println("textafter:" + textAfter);
							//System.out.println("\n");
							
							// stores all correctly matched citations for the given ( )
							List<MetaDoc> parensMetaDocs = new ArrayList<MetaDoc>();
							
							// checks for a # token
							StringTokenizer st = new StringTokenizer(textInBetween, "; "); // TODO: this was just ";" and worked well
							//System.out.println("textinbetween:" + textInBetween);
							
							// iterates through each potential citation chunk (e.g., och and ney, 2005; klein, 2004; blah, 2001)
							while (st.hasMoreTokens()) {
								
								// iterates through each token within the given, potential citation chunk
								String chunk = st.nextToken();
								String year = "";
								String foundCitation = "";
								// stores the hit counts for each doc which seems to match both year and a token
								Map<String, Integer> potentialDocCounts = new HashMap<String, Integer>();
								List<String> tokens = new ArrayList<String>(); // stores tokens
								
								//System.out.println("chunk:" + chunk);
								StringTokenizer st2 = new StringTokenizer(chunk, ",. ");
								
								
								while (st2.hasMoreTokens()) {
									String token = st2.nextToken();
									
									boolean isDigit = true;
									for (int j=0; j<token.length(); j++) {
										if (!Character.isDigit(token.charAt(j))) {
											isDigit = false;
											break;
										}
									}
									
									if (isDigit) {
										// if year is 2 digits and after 1960
										if (token.length() == 2 && Integer.parseInt(token) > 60) {
											year = "19" + token;
										} else if (token.length() == 2 && Integer.parseInt(token) < 15) {
											year = "20" + token;
										} else if (token.length() == 4 && ((Integer.parseInt(token) > 1960) || Integer.parseInt(token) < 2015)) {
											year = token;
										}
									// only store non-numeric tokens (i.e, years and 100% numeric tokens would not get stored)
									} else if (token.length() > 1){
										tokens.add(token);
									}
								}
								
								// if we have a year, then let's try to match against potential citations!
								if (!year.equals("")) {
									
									// saves/updates year -> <sentence #, sentence #>
									Set<Integer> tmpSentNums = new HashSet<Integer>();
									if (yearToSentenceNumber.containsKey(year)) {
										tmpSentNums = yearToSentenceNumber.get(year);
									}
									tmpSentNums.add(contentPerSentence.size());
									yearToSentenceNumber.put(year,  tmpSentNums);
									
									
									Set<String> sources = reportToSources.get(docID);
									List<MetaDoc> metaDocsMatchingYear = new ArrayList<MetaDoc>();
									
									//System.out.println("looking up metadocs from the yr:" + year);
									
									// iterates over all cited Sources
									for (String sourceID : sources) {
									
										//System.out.println("sourceID:" + sourceID);
										//System.out.println("\tmetadoc:" + docIDToMeta.get(sourceID));
										// checks if the legit cited Source matches our ( )'s year
										if (docIDToMeta.get(sourceID).year.equals(year)) {
											metaDocsMatchingYear.add(docIDToMeta.get(sourceID));
										}
									}
									
									// tries to match our ( )'s current chunk's tokens with metaDocsMatchingYear
									for (String token : tokens) {
										
										for (MetaDoc md : metaDocsMatchingYear) {
											if (md.names.contains(token)) {
												// increment our potentialDocCounts
												if (potentialDocCounts.containsKey(md.docID)) {
													potentialDocCounts.put(md.docID, potentialDocCounts.get(md.docID)+1);
												} else {
													potentialDocCounts.put(md.docID, 1);
												}
											}
										}
									}
									
									//System.out.println("chunk: " + chunk + " matched the following potentialDocCounts:");
									Iterator it = sortByValueDescending(potentialDocCounts).keySet().iterator();
									String bestMatch = "";
									while (it.hasNext()) {
										String curDocMatch = (String)it.next();
										//System.out.println(curDocMatch + " => " + potentialDocCounts.get(curDocMatch));
										if (bestMatch.equals("")) {
											bestMatch = curDocMatch; // only set it to the 1st/best one
										}
									}
									
									// if we had any matches
									if (!bestMatch.equals("")) {
										parensMetaDocs.add(docIDToMeta.get(bestMatch));
									}
								}
							} // end of going through each 'chunk' within the given ( ) block
							
							// stores ( )'s content -> <List of MetaDocs which we believe match>
							parenToMetaDocList.put(textInBetween, parensMetaDocs);
							
							// if there's any doc match at all within ( ) then let's NOT add this text to the sentence
							//System.out.println("\t(" + textInBetween + ")");
							if (parensMetaDocs.size() > 0) {
								
								// adds all of our matched cited docs
								for (MetaDoc md : parensMetaDocs) {
									curSetPerSentence.add(md.docID);
									//System.out.println("\tadding:" + md.docID);
									if (citationToMatched.containsKey(md.docID)) {
										citationToMatched.put(md.docID, true);
									}
								}
								//System.out.println("\t\tfound matches:" + parensMetaDocs);
								//System.out.println("\t\t\tsentence was:" + curSentence);
								
								curSentence = curSentence.replace(textInBetween, "");
								//System.out.println("\t\t\tsentence now:" + curSentence);
							} else {
								//System.out.println("\t\tfound 0 matches");
							}
						} // end of checking if the ) is < the maxParenSize.
						  // this might be pointless now because before this if-block, i check to see if we
						  // are beyond this point, in which case we clear all ( indices
						startingParens.remove(startingParens.size()-1);
					} // end of 'if we have a starting Paren'
				// end of checking for ')'
				} else {
					curSentence += rawContent.charAt(i);
				}
			} // end of stepping through every character of the doc's content
			
			// let's add the last bit, if there is any
			if (curSentence.length() > 0) {
				String cleanedSentence = "";
				// looks for year and cleans up tokens
				StringTokenizer st2 = new StringTokenizer(curSentence);
				while (st2.hasMoreTokens()) {
					String token = st2.nextToken();
					
					// checks if we have a new curSectionNum or sectionName
					if (token.startsWith("AAAAA_")) {
						StringTokenizer tmpA = new StringTokenizer(token, "_");
						if (tmpA.countTokens() >= 3) {
							tmpA.nextToken();
							curSectionNum = Integer.parseInt(tmpA.nextToken());
							curSectionName = "";
							while (tmpA.hasMoreTokens()) {
								curSectionName += tmpA.nextToken() + " ";
							}
							curSectionName = curSectionName.trim();
							continue;
						}
					}
					cleanedSentence += token + " ";
				}
				
				cleanedSentence = cleanedSentence.trim();
				
				// saves away our currentSentence AND its doc IDs
				contentPerSentence.add(cleanedSentence);
				Set<String> newCopy = new HashSet<String>(curSetPerSentence);
				citedListPerSentence.add(newCopy);
				//System.out.println("WE ARE UPDATING citedListPerSentence and now have a size:" + citedListPerSentence.size() + " = " + cleanedSentence);
				curSentence = "";
				curSetPerSentence.clear();
				
				
				// adds the section num and name
				// checks for a new curSectionNum and sectionName
				sectionNumPerSentence.add(curSectionNum);
				sectionNamePerSentence.add(curSectionName);
			}
			
			// attempts to find the remaining sources, now that we've gone through all ( ) blocks and removed their content after matching
			for (String source : citationToMatched.keySet()) {
				if (citationToMatched.get(source) == false) {
					MetaDoc md = docIDToMeta.get(source);
					//System.out.println("looking to fill:" + md);
					String year = md.year;
					if (yearToSentenceNumber.containsKey(year)) {
						
						// searches for both the year and any of the authors name.
						// if both are found (year may have been subsequently removed),
						// then we add the citation and remove the year/author from the sentence text
						for (Integer sentenceNum : yearToSentenceNumber.get(year)) {
							
							String sentence = contentPerSentence.get(sentenceNum);
							
							boolean foundCitation = false;
							if (sentence.contains(year)) {
								//System.out.println("sentence: " + sentenceNum + " contains year:" + year);
								for (String authorToken : md.names) {
									if (sentence.contains(authorToken)) {

										foundCitation = true;
							
										break;
									}
								}
							}
							
							// if we found the citation, let's update the:
							// content and the citations
							if (foundCitation) {
								
								Set<String> tmpCited = citedListPerSentence.get(sentenceNum);
								tmpCited.add(md.docID);
								citedListPerSentence.remove(sentenceNum.intValue());
								citedListPerSentence.add(sentenceNum, tmpCited);
								citationToMatched.put(source, true);
							}
							
						}
					}
				}
			}
			
			// now goes through and removes all author and year information from the sentences which contain them
			for (int i=0; i<citedListPerSentence.size(); i++) {
				Set<String> citations = citedListPerSentence.get(i);
				String sentence = contentPerSentence.get(i);
				for (String sourceID : citations) {
					MetaDoc md = docIDToMeta.get(sourceID);
					
					// removes year
					while (sentence.contains(md.year)) {
						sentence = sentence.replace(md.year, "");
					}					
					
					for (String authorToken : md.names) {
						// removes each author
						while (sentence.contains(authorToken)) {
							sentence = sentence.replace(authorToken, "");
						}
					}
				}
				contentPerSentence.remove(i);
				contentPerSentence.add(i, sentence);
			}
			
			// makes the total content now that we've removed citation information from it
			String entireDocContent = "";
			for (String eachSentence : contentPerSentence) {
				entireDocContent += eachSentence + " ";
			}
			
			// replaces all badcharacters with " "
			for (String badChar : badCharacters) {
				entireDocContent = entireDocContent.replaceAll(badChar, " ");
			}
			
			StringTokenizer st = new StringTokenizer(entireDocContent);
			String pureContent = "";
			int numTokensUsed = 0;
			if (st.countTokens() >= minNumWordsInDoc) {
				while (st.hasMoreTokens() && numTokensUsed < maxNumWordsInDoc) {
					
					String token = st.nextToken();
					if (token.length() > 1) { // && !stopwords.contains(token)) {
						
						boolean isDigit = true;
						for (int j=0; j<token.length(); j++) {
							if (!Character.isDigit(token.charAt(j))) {
								isDigit = false;
								break;
							}
						}
						
						if (!isDigit) {
							pureContent += " " + token;
							numTokensUsed++;
							
							if (numTokensUsed >= maxNumWordsInDoc) {
								break;
							}
						}
					}
				}
			}
			pureContent = pureContent.trim();	
			d = new ACLDocument(pureContent, citationToMatched, citedListPerSentence, sectionNumPerSentence, sectionNamePerSentence, contentPerSentence, parenToMetaDocList);
		}
		return d;
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
	
	static Map sortByValueDescending(Map map) {
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

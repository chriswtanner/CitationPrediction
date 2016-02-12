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
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.swing.text.AbstractDocument.Content;


public class ACLMasterSplitter2 {
	
	// input
	public static String aclNetworkFile = "/Users/christanner/research/data/aan/release/2013/acl.txt";
	public static String aclMetaFile = "/Users/christanner/research/data/aan/release/2013/acl-metadata.txt";
	public static String aclDir = "/Users/christanner/research/data/aan/papers_text/";
	public static String stopwordsFile = "/Users/christanner/research/data/aan/stopwords.txt";
	public static int minNumWordsInDoc = 500;
	public static int maxNumWordsInDoc = 9999;
	public static int maxDocsToCompriseCorpus = 20000;
	
	public static int minNumDocsPerWord = 3;
	public static double maxPercentageDocsPerWord = .5;
	
	public static double trainingPercentage = 0.9;
	
	// output
	public static String corpusStatsFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + "_corpus_stats.csv";
	public static String malletOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + "-mallet.txt";
	public static String contentOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".content";
	public static String trainingOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".training";
	public static String testingOutFile = "/Users/christanner/research/projects/CitationFinder/eval/acl_" + maxDocsToCompriseCorpus + ".testing";
	
	// global vars
	public static Set<String> stopwords = new HashSet<String>();
	private static List<String> badCharacters = new ArrayList<String>(Arrays.asList("\\(see", "@", "\\.", "\\/", "\\?", "!", "\\%", "above\\.\\)", "\\.\\)", "“", "_", " ", "…", "”", "‘", "’", ",", "\"", "–", "\\(", "\\)", "\\[", "\\]", ":", ";", "-", "<p>", "\\|", "\\+", "<\\/p>", "\\n", "<\\\\p>"));
	private static List<String> badSpaces = new ArrayList<String>(Arrays.asList("http", "www")); 
	//private static List<String> endPunctuation = new ArrayList<String>(Arrays.asList(".", "!", "?"));
	
	private static Map<String, MetaDoc> docIDToMeta = new HashMap<String, MetaDoc>();
	public static Set<String> allValidDocs = new HashSet<String>(); // docs that have both metadoc info and are found within original citation/referential file
	
	public static Map<String, ACLDocument> aclDocs = new HashMap<String, ACLDocument>(); // all ACLDocuments that have > minNumWordsInDoc, and have metadoc info, and are found within original citation/ref file
	
	public static TreeMap<String, Set<String>> yearToValidDocs = new TreeMap<String, Set<String>>(); // stores year - > docs which we have metadoc info for (but we don't know if it has citation/referential info)
	public static Map<String, Set<String>> authorToValidDocs = new HashMap<String, Set<String>>(); // stores author -> docs which we have metadoc info for (but we don't know if it has citation/referential info)
	public static Map<String, Integer> authorToNumValidDocs = new HashMap<String, Integer>();
	
	public static Map<String, Set<String>> allValidReportToSources = new HashMap<String, Set<String>>(); // all doc -> docs which have metadata for all docs
	
	public static Set<String> corpus = new HashSet<String>(); // stores docs' names (reports and sources)
	private static Map<String, Set<String>> reportToSourcesInCorpus = new HashMap<String, Set<String>>();

	static Set<String> everQueued = new HashSet<String>();
	
	public static Map<String, Integer> authorToNumCorpusLinks = new HashMap<String, Integer>();
	public static Map<String, Integer> authorToNumCorpusReports = new HashMap<String, Integer>();
	
	public static Set<String> badWords = new HashSet<String>(); // stores bad words
	
	private static Map<String, Integer> wordToID = new HashMap<String, Integer>(); // stores the word -> unique ID for our final, good words that comprises our corpus
	public static void main(String[] args) throws IOException {
		
		stopwords = loadList(stopwordsFile);
		
		loadMetaDocs(aclMetaFile);
		
		loadReferential(aclNetworkFile);
		
		System.out.println("finished; numReportS: " + allValidDocs.size());
		System.out.println("# of metadocs: " + docIDToMeta.keySet().size());
		
		System.out.println(allValidDocs.size());
		parseACLDocuments(aclDir);
		
		// clean up allValidDocs (in allValidDocs, yearToAllDocs, authorToAllDocs, allReportToSources)
		cleanUpValidDocs();
		
		constructCorpus(maxDocsToCompriseCorpus);

		updateCorpusWithFilteredWords();
		
		writeCorpus();
	}

	
	
	private static void writeCorpus() throws IOException {
		
		BufferedWriter bMallet = new BufferedWriter(new FileWriter(malletOutFile));
		BufferedWriter bContent = new BufferedWriter(new FileWriter(contentOutFile));
		
		// writes .content file (for PMTLM)		
		for (String doc : corpus) {
			bContent.write(doc);
			ACLDocument a = aclDocs.get(doc);
			for (String w : a.wordCount.keySet()) {
				if (!badWords.contains(w)) {
					bContent.write(" " + wordToID.get(w) + " " + a.wordCount.get(w));
				}
			}
			bContent.write(" 0\n"); // writes pointless, but mandatory, 'label' i.e., topic id for PMTLM
			
			bMallet.write(doc + " " + doc + " " + a.pureContent + "\n");
		}
		bContent.close();
		bMallet.close();
		
		// writes .training and .testing
		// NOTE: we ensure that every testing-source(link) has been seen during training.  this is akin to ensuring that every observable word has been seen; for a published paper,
		// this is a little iffy, as we'd instead just do padding for unseen features.  or better yet, represent likelihood of a connection based on other properties
		Random rand = new Random();
		int numLinks = 0;
		int numTraining = 0;
		int numTesting = 0;
		BufferedWriter bTraining = new BufferedWriter(new FileWriter(trainingOutFile));
		BufferedWriter bTesting = new BufferedWriter(new FileWriter(testingOutFile));
		
		Set<String> trainingSources = new HashSet<String>();
		for (String re : reportToSourcesInCorpus.keySet()) {
			for (String s : reportToSourcesInCorpus.get(re)) {
				
				if (!trainingSources.contains(s)) {
					bTraining.write(s + " " + re + "\n");
					trainingSources.add(s);
					numTraining++;
				} else {
					if (((double)numTraining / (double)(numTraining + numTesting + 0.001)) < trainingPercentage) {
						bTraining.write(s + " " + re + "\n");
						numTraining++;
					} else {
						bTesting.write(s + " " + re + "\n");
						numTesting++;
					}
				}
				numLinks++;
			}
		}
		System.out.println("total links: " + numLinks);
		System.out.println("# training: " + numTraining + " (" + (double)numTraining/numLinks + ")");
		bTraining.close();
		bTesting.close();
		
		// writes corpus stats
		BufferedWriter bStats = new BufferedWriter(new FileWriter(corpusStatsFile));
		bStats.write("total docs: " + corpus.size() + "\n");
		bStats.write("# unique word types: " + wordToID.keySet().size() + "\n");
		bStats.write("total unique authors (multi authors per doc): " + authorToNumCorpusReports.keySet().size() + "\n");
		java.util.Iterator it = sortByValueDescending(authorToNumCorpusReports).keySet().iterator();
		bStats.write("\n\nauthor,# reports, #links\n");
		while (it.hasNext()) {
			String au = (String)it.next();
			bStats.write(au.replaceAll(",", "_") + "," + authorToNumCorpusReports.get(au) + "," + authorToNumCorpusLinks.get(au) + "\n");
		}
		bStats.close();
	}

	private static void updateCorpusWithFilteredWords() {
		
		Map<String, Set<String>> wordToDocs = new HashMap<String, Set<String>>();
		
		for (String doc : corpus) {
			String content = aclDocs.get(doc).pureContent;
			StringTokenizer st = new StringTokenizer(content);
			
			Map<String, Integer> wordCount = new HashMap<String, Integer>();
			while (st.hasMoreTokens()) {
				String w = st.nextToken();
				
				// updates word -> {docs}
				Set<String> tmp = new HashSet<String>();
				if (wordToDocs.containsKey(w)) {
					tmp = wordToDocs.get(w);
				}
				tmp.add(doc);
				wordToDocs.put(w, tmp);
				
				// updates wordcount for doc
				if (wordCount.containsKey(w)) {
					wordCount.put(w, wordCount.get(w)+1);
				} else {
					wordCount.put(w, 1);
				}
			}
			ACLDocument a = aclDocs.get(doc);
			a.setWordCount(wordCount);
			aclDocs.put(doc, a);
			
		}
		int maxNumDocs = (int) Math.floor(corpus.size()*maxPercentageDocsPerWord);
		

		for (String w : wordToDocs.keySet()) {
			int numDocs = wordToDocs.get(w).size();
			if (numDocs < minNumDocsPerWord) {
				badWords.add(w);
				//System.out.println(w + " too few");
			} else if (numDocs > maxNumDocs) {
				badWords.add(w);
				System.out.println(w + " appeared in " + (double)numDocs/(double)corpus.size());
			} else {
				
				// gets a unique word ID for it
				if (!wordToID.containsKey(w)) {
					int wordID = wordToID.keySet().size();
					wordToID.put(w, wordID);
				}
			}
		}
		
		System.out.println("total unique words: " + wordToDocs.keySet().size() + " of which, " + badWords.size() + " appeared too few or often, leaving us with: " + (wordToDocs.keySet().size()-badWords.size()) + " new, good words");
		System.out.println("wordID size:" + wordToID.keySet().size());
		for (String doc : corpus) {
			ACLDocument a = aclDocs.get(doc);
			String content = a.pureContent;
			String filteredContent = "";
			StringTokenizer st = new StringTokenizer(content);
			while (st.hasMoreTokens()) {
				String w = st.nextToken();
				if (!badWords.contains(w)) {
					filteredContent += (w + " ");
				}
			}
			filteredContent = filteredContent.trim();
			a.updateContent(filteredContent);
			System.out.println(doc + ", finally, has " + a.numWords + " total words");
			aclDocs.put(doc, a);
		}
	}

	private static void constructCorpus(int maxDocs) {
		
		List<String> queue = new ArrayList<String>();

		Map<Integer, Set<String>> levelToDocs = new HashMap<Integer, Set<String>>();
		Map<String, Integer> docToLevel = new HashMap<String, Integer>();
		
		String bestDoc = getNextDoc();
		if (bestDoc.equals("")) {
			System.err.println("no good docs remaining!");	
		} else {
			queue.add(0, bestDoc);
			everQueued.add(bestDoc);
			Set<String> tmp = new HashSet<String>();
			tmp.add(bestDoc);
			levelToDocs.put(0, tmp);
			docToLevel.put(bestDoc, 0);
		}
		
		while (corpus.size() < maxDocs) {

			if (queue.isEmpty()) {
				bestDoc = getNextDoc();
				if (bestDoc.equals("")) {
					System.err.println("no good docs remaining!");
					break;
				} else {
					queue.add(0, bestDoc);
					everQueued.add(bestDoc);
					Set<String> tmp = levelToDocs.get(0);
					tmp.add(bestDoc);
					levelToDocs.put(0, tmp);
					docToLevel.put(bestDoc, 0);
				}
			}
			//System.out.println("queue size: " + queue.size());
			String doc = queue.get(0);
			queue.remove(0);
			int rootsLevel = docToLevel.get(doc);
			//System.out.println("post-pop: queue size: " + queue.size());
			//System.out.println("building corpus; popped " + doc + ", which has " + allValidReportToSources.get(doc).size() + " sources");
			for (String source : allValidReportToSources.get(doc)) {
				
				// updates our to-be-returned report -> {sources} map
				Set<String> curSources = new HashSet<String>();
				if (reportToSourcesInCorpus.containsKey(doc)) {
					curSources = reportToSourcesInCorpus.get(doc);
				}
				curSources.add(source);
				reportToSourcesInCorpus.put(doc, curSources);
				
				corpus.add(doc);
				corpus.add(source);

				Set<String> tmp = new HashSet<String>();
				if (levelToDocs.containsKey(rootsLevel+1)) {
					tmp = levelToDocs.get(rootsLevel+1);
				}
				tmp.add(source);
				levelToDocs.put(rootsLevel+1, tmp);
				docToLevel.put(source, rootsLevel+1);
				
				// queue breadth-first
				// don't queue duplicate items
				if (!everQueued.contains(source) && allValidReportToSources.containsKey(source)) {
					queue.add(queue.size(), source);
					everQueued.add(source);
				}
			}
		}
		
		// prints levels
		
		for (Integer level : levelToDocs.keySet()) {
			System.out.println(level + ":");
			for (String doc : levelToDocs.get(level)) {
				System.out.println("\t" + doc);
			}
		}
		
		// prints our corpus' ref
		for (String rep : reportToSourcesInCorpus.keySet()) {
			System.out.println("report: " + rep);
			for (String s : reportToSourcesInCorpus.get(rep)) {
				
				// puts a * by the source if it's also a report (i.e., has citations of its own)
				if (reportToSourcesInCorpus.containsKey(s)) {
					System.out.println("\t" + s + "*");
				} else {
					System.out.println("\t" + s);
				}
				
			}
		}

		// prints corpus
		System.out.println("total corpus size: " + corpus.size());
		
		for (String re : reportToSourcesInCorpus.keySet()) {
			int numLinks = reportToSourcesInCorpus.get(re).size();
			for (String au : docIDToMeta.get(re).names) {
				if (authorToNumCorpusReports.containsKey(au)) {
					authorToNumCorpusReports.put(au, authorToNumCorpusReports.get(au)+1);
					authorToNumCorpusLinks.put(au, authorToNumCorpusLinks.get(au)+numLinks);
				} else {
					authorToNumCorpusReports.put(au, 1);
					authorToNumCorpusLinks.put(au, numLinks);
				}
			}
			
		}
		
		java.util.Iterator it = sortByValueDescending(authorToNumCorpusReports).keySet().iterator();
		while (it.hasNext()) {
			String au = (String)it.next();
			System.out.println(au + " has " + authorToNumCorpusReports.get(au) + " reports, totalling " + authorToNumCorpusLinks.get(au) + " # links to sources which are also in our corpus");
		}
		                                                   
	}

	// returns the doc (that hasn't been chosen for our corpus yet) from a prolific artist and recent year
	private static String getNextDoc() {
		String retDoc = "";
		java.util.Iterator it = sortByValueDescending(authorToNumValidDocs).keySet().iterator();
		while (it.hasNext()) {
			
			String au = (String)it.next();
			//System.out.println(au + " has " + authorToValidDocs.get(au).size() + " docs");
			// finds the best (i.e., most recent) non-already-chosen doc from the author
			int latestYear = 0;
			String bestDoc = "";
			for (String doc : authorToValidDocs.get(au)) {
				if (!corpus.contains(doc) && allValidReportToSources.containsKey(doc) && !everQueued.contains(doc)) {
					int curYear = Integer.parseInt(docIDToMeta.get(doc).year);
					//System.out.println(doc + "; year:" + curYear);
					if (curYear > latestYear) {
						latestYear = curYear;
						bestDoc = doc;
					}
				}
			}
			if (!bestDoc.equals("")) {
				//System.out.println("getNextDoc() selected " + au + "'s " + bestDoc + " who has " + authorToValidDocs.get(au).size() + " docs, and we are returning the 1 from " + latestYear);
				return bestDoc;
			}
		}

		return retDoc;
	}

	private static void cleanUpValidDocs() {
		allValidDocs.clear();
		allValidDocs = aclDocs.keySet();
		System.out.println(allValidDocs.size());
		
		// updates year -> {docs}
		Set<String> yearsToRemove = new HashSet<String>();
		for (String year : yearToValidDocs.keySet()) {
			Set<String> newDocs = new HashSet<String>();
			for (String doc : yearToValidDocs.get(year)) {
				if (allValidDocs.contains(doc)) {
					newDocs.add(doc);
				}
			}
			
			if (newDocs.size() > 0) {
				yearToValidDocs.put(year, newDocs);
				System.out.println(year + " -> " + newDocs.size() + " docs");
			} else {
				yearsToRemove.add(year);
			}
		}
		for (String yr : yearsToRemove) {
			yearToValidDocs.remove(yr);
		}
		
		// updates author -> {docs}
		Set<String> authorsToRemove = new HashSet<String>();
		for (String author : authorToValidDocs.keySet()) {
			Set<String> newDocs = new HashSet<String>();
			for (String doc : authorToValidDocs.get(author)) {
				if (allValidDocs.contains(doc)) {
					newDocs.add(doc);
				}
			}
			
			if (newDocs.size() > 0) {
				authorToValidDocs.put(author, newDocs);
				authorToNumValidDocs.put(author, newDocs.size());
				System.out.println(author + " -> " + newDocs.size() + " docs");
			} else {
				authorsToRemove.add(author);
			}
		}
		for (String au : authorsToRemove) {
			authorToValidDocs.remove(au);
		}
		
		
		// updates report -> {sources}
		Set<String> reportsToRemove = new HashSet<String>();
		for (String report : allValidReportToSources.keySet()) {
			System.out.println(report + " originally had " + allValidReportToSources.get(report).size() + " sources");
			Set<String> newDocs = new HashSet<String>();
			for (String doc : allValidReportToSources.get(report)) {
				if (allValidDocs.contains(doc)) {
					newDocs.add(doc);
				}
			}
			
			if (newDocs.size() > 0) {
				allValidReportToSources.put(report, newDocs);
				System.out.println(report + " -> " + newDocs.size() + " docs: " + newDocs);
			} else {
				reportsToRemove.add(report);
			}
		}
		for (String re : reportsToRemove) {
			allValidReportToSources.remove(re);
		}
		
	}

	// stores all valid, non-null ACL docs in aclDocs map: name -> ACLDoc (all of these also have metafile and citation info from referential)
	private static void parseACLDocuments(String dir) throws IOException {
		
		int numDocs = 0;
		for (String doc : allValidDocs) {
			ACLDocument a = parseACLDocument(dir + doc + ".txt");
			if (a != null && a.numWords > minNumWordsInDoc) {
				aclDocs.put(doc, a);
				numDocs++;
			}
		
			if (numDocs % 100 == 0) {
				System.out.println("parsed: " + numDocs);
			}
			
			/*
			if (numDocs == maxDocsToCompriseCorpus) {
				break;
			}
			*/
		}
		
	}

	private static ACLDocument parseACLDocument(String docFile) throws IOException {
		//System.out.println("parsing " + docFile);
		ACLDocument d = null;

		// if the doc doesn't exist on the hard drive, return ""
		File f = new File(docFile);
		if(f.exists()) {
			BufferedReader bin = new BufferedReader(new FileReader(docFile));
			boolean requireAbstract = false;
			Integer curSectionNum = 0;
			String curSectionName = "";
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
					if (firstToken.length() <= 2 && firstToken.startsWith(String.valueOf(Integer.toString(curSectionNum+1)))){
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

					//rawContent = "AAAAA_0_abstract ";
					continue;
				}
				// let's disregard the content before the introduction
				if (lineNum < 60 && !foundIntroduction && curLine.indexOf("introduction") != -1 && curLine.length() <= 20) {
					
					// if we have no guarantee that we've seen a section title before (i.e., abstract),
					// we should clear out our content now, as it probably contains email header info
					if (!foundAbstract) {
						rawContent = ""; //"AAAAA_1_introduction ";//introduction ";
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

			for (String badToken : badTokens) {
				while (rawContent.indexOf(badToken) != -1) {
					rawContent = rawContent.replace(badToken, " ");
				}
				//rawContent = rawContent.replace(";", " ; ");
				rawContent = rawContent.replaceAll(";", "  ");
			}
			
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
						
						if (!isDigit && !stopwords.contains(token)) {
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
			//System.out.println("doc: " + d);
		}
		return d;
	}
	
	private static void loadReferential(String ref) throws IOException {
		BufferedReader bin = new BufferedReader(new FileReader(ref));

		allValidReportToSources = new HashMap<String, Set<String>>();
		allValidDocs = new HashSet<String>();
		
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
						
			// skip over reports/sources that we don't have both meta-data and mallet info for
			if (!docIDToMeta.containsKey(report) || !docIDToMeta.containsKey(source)) {
				System.out.println("skipping " + report + " and " + source + " because we don't have metadata for at least 1 of them");
				continue;
			}
			
			allValidDocs.add(report);
			allValidDocs.add(source);
			
			// updates our map of Report -> {sources}
			Set<String> curSources = new HashSet<String>();
			if (allValidReportToSources.containsKey(report)) {
				curSources = allValidReportToSources.get(report);
			}
			if (!report.equals(source)) {
				curSources.add(source);
			}
			allValidReportToSources.put(report, curSources);
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
			String docName = st.nextToken();
			docName = docName.replace("{", "").replace("}", "");
			
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
						
						// updates auth -> <docs>
						Set<String> tmpReports = new HashSet<String>();
						if (authorToValidDocs.containsKey(authorToken)) {
							tmpReports = authorToValidDocs.get(authorToken);
						}
						tmpReports.add(docName);
						authorToValidDocs.put(authorToken, tmpReports);
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
			MetaDoc md = new MetaDoc(docName, title, year, authorTokens);
			docIDToMeta.put(docName, md);
			
			// updates the year -> <docs> map
			Set<String> tmpMetadocs = new HashSet<String>();
			if (yearToValidDocs.containsKey(year)) {
				tmpMetadocs = yearToValidDocs.get(year);
			}
			tmpMetadocs.add(docName);
			yearToValidDocs.put(year, tmpMetadocs);
		
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
		for (java.util.Iterator it = list.iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
}

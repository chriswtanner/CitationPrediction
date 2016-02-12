import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class ACLDocument {
	String pureContent = "";
	int numCitationsMatched = 0;
	int numTotalCitations = 0;
	int numWords = 0;
	int numSections = 0;
	double percentageCitationsMatched = 0.0;
	List<Set<String>> citedListPerSentence = new ArrayList<Set<String>>();
	List<Integer> sectionNumPerSentence = new ArrayList<Integer>();
	List<String> sectionNamePerSentence = new ArrayList<String>();
	List<String> rawContentPerSentence = new ArrayList<String>();
	List<String> cleanedContentPerSentence = new ArrayList<String>();
	Map<String, Integer> wordCount = new HashMap<String, Integer>();
	
	Map<String, Boolean> citationToMatched = new HashMap<String, Boolean>();
	Map<String, List<MetaDoc>> parenToMetaDocList = new HashMap<String, List<MetaDoc>>();
	boolean isReport = false;
	
	public void setWordCount(Map<String, Integer> wc) {
		this.wordCount = wc;
	}
	
	public void updateContent(String c) {
		this.pureContent = c;
		StringTokenizer st = new StringTokenizer(c);
		this.numWords = st.countTokens();
		this.isReport = false;
	}
	public ACLDocument(String content) {
		//System.out.println("making a source doc!");
		this.pureContent = content;
		StringTokenizer st = new StringTokenizer(content);
		this.numWords = st.countTokens();
		this.isReport = false;
	}
	
	public ACLDocument(List<Set<String>> citedListPerSentence, List<String> rawContentPerSentence, List<String> cleanedContentPerSentence, Map<String, Boolean> allSources) {
		this.citedListPerSentence = citedListPerSentence;
		this.rawContentPerSentence = rawContentPerSentence;
		this.cleanedContentPerSentence = cleanedContentPerSentence;
		this.citationToMatched = allSources;
	}
	
	public ACLDocument(String content, Map<String, Boolean> citationToMatched,
			List<Set<String>> citedListPerSentence, List<Integer> sectionNumPerSentence,
			List<String> sectionNamePerSentence, List<String> contentPerSentence,
			Map<String, List<MetaDoc>> parenToMetaDocList) {
		this.isReport = true;
		this.pureContent = content;
		StringTokenizer st = new StringTokenizer(content);
		this.numWords = st.countTokens();
		
		this.numTotalCitations = citationToMatched.keySet().size();
		this.numCitationsMatched = 0;
		for (String doc : citationToMatched.keySet()) {
			if (citationToMatched.get(doc)) {
				this.numCitationsMatched++;
			}
		}
		this.percentageCitationsMatched = (double)this.numCitationsMatched / (double)this.numTotalCitations;
		if (sectionNumPerSentence.size() == 0) {
			this.numSections = 0;
		} else {
			this.numSections = sectionNumPerSentence.get(sectionNumPerSentence.size()-1)+1;
		}
		this.citedListPerSentence = citedListPerSentence;
		this.sectionNumPerSentence = sectionNumPerSentence;
		this.sectionNamePerSentence = sectionNamePerSentence;
		this.rawContentPerSentence = contentPerSentence;
		this.citationToMatched = citationToMatched;
		this.parenToMetaDocList = parenToMetaDocList;
	}
	
	public void updateSources(Set<String> goodSources) {
		
		// updates our counts of how many valid sources we have, and the %
		this.numCitationsMatched = 0;
		for (String doc : citationToMatched.keySet()) {
			if (!goodSources.contains(doc)) {
				citationToMatched.put(doc, false);
			}
			
			if (citationToMatched.get(doc)) {
				this.numCitationsMatched++;
			}
		}
		this.percentageCitationsMatched = (double)this.numCitationsMatched / (double)this.numTotalCitations;
		
		// updates the gold labels for each sentence (we remove the bad sources)
		List<Set<String>> newCitedListPerSentence = new ArrayList<Set<String>>();
		for (int i=0; i<citedListPerSentence.size(); i++) {
			Set<String> tmp = citedListPerSentence.get(i);
			Set<String> newSources = new HashSet<String>();
			for (String doc : tmp) {
				
				// checks if a sentence contains a good source
				if (goodSources.contains(doc)) {
					newSources.add(doc);
				}
			}
			newCitedListPerSentence.add(newSources);
		}
		this.citedListPerSentence.clear();
		this.citedListPerSentence = newCitedListPerSentence;		
	}
	
	
	public String toString() {
		return this.numWords + " " + this.pureContent; 
	}
	/*
	public String toString() {
		return "\tisReport? " + isReport + "; # words: " + this.numWords + "; # sentences: " + this.citedListPerSentence.size() + "; # sections: " + this.numSections + "; matched citations: " + this.numCitationsMatched + " of " + this.numTotalCitations + " = (" + this.percentageCitationsMatched + ")"; 
	}
	*/

	// goes through the content per sentence and creates a parallel version which removes:
	// - stopwords
	// - non-valid words (i.e., the words which don't appear in both a report and source)
	// also does the same to update our 'pureContent' variable, which is used for mallet -- it's a 1 string dump of the text
	public void updateContent(Set<String> validWords, Set<String> stopwords, int minNumWordsPerCleanedSentence) {
		
		if (this.isReport) {
			cleanedContentPerSentence = new ArrayList<String>();
			String tmpPureContent = "";
			
			for (String line : rawContentPerSentence) {
				// cleans up the line by removing:
				// - stopwords
				// - non-valid words (i.e., words which are not in both a report and a source)
				String filteredContent = "";
				StringTokenizer st = new StringTokenizer(line);
				while (st.hasMoreTokens()) {
					String w = (String)st.nextToken();
					if (validWords.contains(w) && !stopwords.contains(w)) {
						filteredContent += w + " ";
					}
				}
				filteredContent = filteredContent.trim(); // removes the trailing space
				cleanedContentPerSentence.add(filteredContent);
				tmpPureContent += " " + filteredContent;
			}
			tmpPureContent = tmpPureContent.trim();
			
			// sets our new pureContent, which as been filtered through all stopwords and non-validwords
			this.pureContent = tmpPureContent;
			StringTokenizer st = new StringTokenizer(this.pureContent);
			this.numWords = st.countTokens();
			
			// now, some of the cleaned content lines may have very few (or even none) tokens,
			// so let's merge sentences together.  this could get messy to do in-place,
			// so for correctness, i'll just tmp. create new data structures for both the raw and cleaned sentences.
			// ah, i must also merge the other lists which correspond to each sentence
			int curNumTokens = 0;
			List<Integer> curSentences = new ArrayList<Integer>();
			
			List<List<Integer>> sentencesMerged = new ArrayList<List<Integer>>();
			
			for (int i=0; i<cleanedContentPerSentence.size(); i++) {
				String line = cleanedContentPerSentence.get(i);
				st = new StringTokenizer(line);
				int numTokens = st.countTokens();
				
				// adds to our running line
				curNumTokens += numTokens;
				curSentences.add(i);
				
				// checks if we've reached the min threshold
				if (curNumTokens >= minNumWordsPerCleanedSentence) {
					sentencesMerged.add(curSentences);
					curSentences = new ArrayList<Integer>();
					curNumTokens = 0;
				}
			}
			
			// checks if we have a trailing sentence which didn't meet the threshold.
			// if so, oh well, let's add it anyway.  who knows, maybe we can still predict the citation
			if (curNumTokens > 0 && curSentences.size() > 0) { // the latter statement will always be true, but i'm pointlessly checking
				sentencesMerged.add(curSentences);
			}
			
			// let's make them now that we know which sentences should be merged
			List<String> newRawContentPerSentence = new ArrayList<String>();
			List<String> newCleanedContentPerSentence = new ArrayList<String>();
			List<Set<String>> newCitedListPerSentence = new ArrayList<Set<String>>();
			List<Integer> newSectionNumPerSentence = new ArrayList<Integer>();
			List<String> newSectionNamePerSentence = new ArrayList<String>();
			for (List<Integer> sentences : sentencesMerged) {
				
				String newRawContent = "";
				String newCleanedContent = "";
				Set<String> newCitedList = new HashSet<String>();
				int sectionNum = 0; // takes the last-most
				String sectionName = ""; // takes the last-most
				for (Integer sentNum : sentences) {
					newRawContent += "  " + rawContentPerSentence.get(sentNum);
					newCleanedContent += " " + cleanedContentPerSentence.get(sentNum);
					
					for (String citation : citedListPerSentence.get(sentNum)) {
						newCitedList.add(citation);
					}
					
					sectionNum = sectionNumPerSentence.get(sentNum);
					sectionName = sectionNamePerSentence.get(sentNum);
				}
				// cleans up strings
				newRawContent = newRawContent.trim();
				newCleanedContent = newCleanedContent.trim();
				
				// adds to our new variables
				newRawContentPerSentence.add(newRawContent);
				newCleanedContentPerSentence.add(newCleanedContent);
				newCitedListPerSentence.add(newCitedList);
				newSectionNumPerSentence.add(sectionNum);
				newSectionNamePerSentence.add(sectionName);
			}
			
			// updates our global vars
			rawContentPerSentence = newRawContentPerSentence;
			cleanedContentPerSentence = newCleanedContentPerSentence;
			citedListPerSentence = newCitedListPerSentence;
			sectionNumPerSentence = newSectionNumPerSentence;
			sectionNamePerSentence = newSectionNamePerSentence;
		} else { // represents a source
			
			//String tmpPureContent = "";
			
			//for (String line : rawContentPerSentence) {
				// cleans up the line by removing:
				// - stopwords
				// - non-valid words (i.e., words which are not in both a report and a source)
			String filteredContent = "";
			StringTokenizer st = new StringTokenizer(this.pureContent);
			while (st.hasMoreTokens()) {
				String w = (String)st.nextToken();
				if (validWords.contains(w) && !stopwords.contains(w)) {
					filteredContent += w + " ";
				}
			}
			filteredContent = filteredContent.trim(); // removes the trailing space
				//cleanedContentPerSentence.add(filteredContent);
				//tmpPureContent += " " + filteredContent;
			//}
			//tmpPureContent = tmpPureContent.trim();
			
			// sets our new pureContent, which has been filtered through all stopwords and non-validwords
			this.pureContent = filteredContent; //tmpPureContent;
			st = new StringTokenizer(this.pureContent);
			this.numWords = st.countTokens();
		}
	}
}

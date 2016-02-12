import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PMTLMDriver {

	public static void main(String[] args) throws IOException {
		
		// PMTLM's input files
		String malletInputFile = "/Users/christanner/research/projects/CitationFinder/input/mallet-input.txt";
		String stopwords = "/Users/christanner/research/projects/CitationFinder/input/stopwords.txt";

		// PMTLM's output/saved object
		String pmtlmObject = "/Users/christanner/research/projects/CitationFinder/input/pmtlm_50z_200i.ser";

		// NOTE: PMTLM variables/params are in the LDA's class as global vars
		PMTLM p = new PMTLM(malletInputFile, stopwords);
		p.runEM();
		p.saveModel(pmtlmObject);
	}

}

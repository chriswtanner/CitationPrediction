import java.io.IOException;


public class LDADriver {

	public static void main(String[] args) throws IOException {
		
		// NOTE: simply change this value
		String corpus = "acl_20000";
		
		// LDA's input files
		String malletInputFile = "/Users/christanner/research/projects/CitationFinder/eval/" + corpus + "-mallet.txt"; // mallet-input.txt";
		String stopwords = "/Users/christanner/research/projects/CitationFinder/input/stopwords.txt";

		// LDA's output/saved object
		String ldaObject = "/Users/christanner/research/projects/CitationFinder/eval/lda_" + corpus + "_2000i.ser";
		
		// NOTE: LDA variables/params are in the LDA's class as global vars
		LDA l = new LDA(malletInputFile, stopwords);
		l.runLDA();
		l.saveLDA(ldaObject);
	}

}

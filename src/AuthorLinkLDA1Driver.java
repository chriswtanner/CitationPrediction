import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class AuthorLinkLDA1Driver {
	public static void main(String[] args) throws IOException {
		
		// NOTE: simply change this value
		String corpus = "acl_20000";
		boolean firstAuthor = false;
		//String dataDir = "/Users/christanner/research/projects/CitationFinder/eval/";
		String dataDir = "/users/ctanner/data/ctanner/";
		
		// input files
		String model = "authorLinkLDA1";
		String malletInputFile = dataDir + corpus + "-mallet.txt";
		String stopwords = dataDir + "stopwords.txt";
		String trainingFile = dataDir + corpus + ".training";
		String testingFile = dataDir + corpus + ".testing"; // only for ensuring we have
		String metaFile = dataDir + "acl-metadata.txt";
		
		// output/saved object
		String authorLinkLDA1Object = dataDir + model + "_" + corpus + "_2000i.ser";
		
		// NOTE: model variables/params are in the LDA's class as global vars
		// NOTE2: we only pass testingFile for debugging; to ensure we have all authors in training that will be seen in testing
		double alpha = 0;
		int numIterations = 0;
		int gamma = 5;
		if (args.length > 0) {
			alpha = Double.parseDouble(args[0]);
			numIterations = Integer.parseInt(args[1]);
			gamma = Integer.parseInt(args[2]);
			authorLinkLDA1Object = dataDir + model + "_" + corpus + "_" + alpha + "_" + gamma + "_" + numIterations + ".ser";
			System.out.println("running "+  model + " w/ alpha: " + alpha + "; # iterations: " + numIterations);
		}
		
		
		AuthorLinkLDA1 a = new AuthorLinkLDA1(alpha, numIterations, gamma, firstAuthor, malletInputFile, trainingFile, testingFile, stopwords, metaFile);
		a.runEM();
		a.saveModel(authorLinkLDA1Object);
		a.printTopics(0);
		a.printTopics(1);
	}
}

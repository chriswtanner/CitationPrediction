import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class AuthorLinkLDA3Driver {
	public static void main(String[] args) throws IOException {
		
		// NOTE: simply change this value
		String corpus = "acl_5000";
		
		// PLSA's input files
		String model = "authorLinkLDA3";
		//String dataDir = "/Users/christanner/research/projects/CitationFinder/eval/";
		String dataDir = "/users/ctanner/data/ctanner/";
		boolean firstAuthor = false;
		
		String malletInputFile = dataDir + corpus + "-mallet.txt";
		String stopwords = dataDir + "stopwords.txt";
		String trainingFile = dataDir + corpus + ".training";
		String metaFile = dataDir + "acl-metadata.txt";
		
		// output/saved object
		String authorLinkLDA3Object = dataDir + model + "_" + corpus + "_2000i.ser";
		String statsFile = "/Users/christanner/research/projects/CitationFinder/eval/" + model + "_" + corpus + "_stats.txt";
		
		
		double alpha = 0;
		int numIterations = 0;
		if (args.length > 0) {
			alpha = Double.parseDouble(args[0]);
			numIterations = Integer.parseInt(args[1]);
			authorLinkLDA3Object = dataDir + model + "_" + corpus + "_" + alpha + "_2000i.ser";
			System.out.println("running "+  model + " w/ alpha: " + alpha + "; # iterations: " + numIterations);
		}
		
		// NOTE: PLSA variables/params are in the LDA's class as global vars
		AuthorLinkLDA3 p = new AuthorLinkLDA3(alpha, numIterations, firstAuthor, malletInputFile, trainingFile, stopwords, metaFile);
		//p.printStats(statsFile);
		//System.exit(1);
		p.runLinkLDA();
		p.saveLinkLDA(authorLinkLDA3Object);
		
		p.printTopics(0);
		p.printTopics(1);

	}
}

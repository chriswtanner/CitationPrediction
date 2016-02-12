import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PLSADriver {
	public static void main(String[] args) throws IOException {
		
		// NOTE: simply change this value
		String corpus = "acl_20000";
		String dataDir = "/Users/christanner/research/projects/CitationFinder/eval/";
		//String dataDir = "/users/ctanner/data/ctanner/";
		boolean linkLDAModel = true;
		
		// PLSA's input files
		String model = "";
		if (linkLDAModel) {
			model = "linkLDA";
		} else {
			model = "plsa";
		}
		String malletInputFile = dataDir + corpus + "-mallet.txt";
		String stopwords = dataDir + "stopwords.txt";
		String trainingFile = dataDir + corpus + ".training";
		
		// PLSA's output/saved object
		String modelObject = dataDir + model + "_" + corpus + "_2000i.ser";
		//String linkLDAObject = "/Users/christanner/research/projects/CitationFinder/eval/" + model + "_" + corpus + "_2000i.ser";
		
		// NOTE: PLSA variables/params are in the LDA's class as global vars
		double alpha = 0;
		int numIterations = 0;
		if (args.length > 0) {
			model = args[0];
			if (model.equals("plsa")) {
				linkLDAModel = false;
			} else {
				linkLDAModel = true;
			}
			alpha = Double.parseDouble(args[1]);
			numIterations = Integer.parseInt(args[2]);
			modelObject = dataDir + model + "_" + corpus + "_" + alpha + "_2000i.ser";
			System.out.println("running "+  model + " w/ alpha: " + alpha + "; # iterations: " + numIterations);
		}
		PLSA p = new PLSA(dataDir, alpha, numIterations, malletInputFile, trainingFile, stopwords, linkLDAModel);

		if (linkLDAModel) {
			p.runLinkLDA();
			p.saveLinkLDA(modelObject);
		} else {
			p.runPLSA();
			p.savePLSA(modelObject);
		}
		
		p.printTopics();

	}
}

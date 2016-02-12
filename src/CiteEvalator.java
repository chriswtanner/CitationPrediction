import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;


public class CiteEvalator {

	static List<Integer> sourcesCutoffs = Arrays.asList(1,10,25,50,100,150,200,250,300,350,400,450,500,1000,2000,4000);
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {

		// NOTE: change these 2 variables per run!
		String corpus = "acl_1000";
		String method = "lda"; //"authorLinkLDA1"; //authorLinkLDA1"; // NOTE: be sure to actually pass the correct object CitationEngine (authorLinkLDA1)

		// input files
		String scoringMethod = method;
		if (method.equals("plsa") || method.equals("lda")) {
			scoringMethod = "bayesian";
		}

		String docsPath = "/Users/christanner/research/data/aan/citation_prediction/";

		String aclMetaFile = "/Users/christanner/research/data/aan/release/2013/acl-metadata.txt"; // /Users/christanner/research/projects/CitationFinder/input/cora-mallet.txt"; // /Users/christanner/research/data/aan/release/2013/acl-metadata.txt";
		String trainingGoldFile = "/Users/christanner/research/projects/CitationFinder/eval/" + corpus + ".training";
		String testingGoldFile = "/Users/christanner/research/projects/CitationFinder/eval/" + corpus + ".testing"; // referential.txt";
		String malletInputFile = "/Users/christanner/research/projects/CitationFinder/eval/" + corpus + "-mallet.txt"; // ONLY USED FOR TOPIC COHERENCE/REDUCTION
		
		// pass 1 of these to the constructor of CitationEngine
		String ldaInput = "/Users/christanner/research/projects/CitationFinder/eval/lda_" + corpus + "_2000i.ser"; //lda_50z_2000i.ser";		
		String plsaInput = "/Users/christanner/research/projects/CitationFinder/eval/plsa_" + corpus + "_0.99_2000i.ser";
		String linkLDAInput = "/Users/christanner/research/projects/CitationFinder/eval/linkLDA_" + corpus + "_0.99_2000i.ser";
		String authorLinkLDA1Input = "/Users/christanner/research/projects/CitationFinder/eval/authorLinkLDA1_" + corpus + "_2000i.ser";
		//String authorLinkLDA2Input = "/Users/christanner/research/projects/CitationFinder/eval/authorLinkLDA2_" + corpus + "_2000i.ser";
		String authorLinkLDA2Input = "/Users/christanner/research/projects/CitationFinder/eval/authorLinkLDA2_" + corpus + "_0.99_2000i.ser";
		//String pmtlmInput = "/Users/christanner/research/projects/CitationFinder/input/pmtlm_50z_200i.ser";
		double alpha = 0;
		if (args.length > 0) {
			alpha = Double.parseDouble(args[0]);
			linkLDAInput = "/Users/christanner/research/projects/CitationFinder/eval/linkLDA_" + corpus + "_" + alpha + "_2000i.ser";
			authorLinkLDA2Input = "/Users/christanner/research/projects/CitationFinder/eval/authorLinkLDA2_" + corpus + "_" + alpha + "_2000i.ser";
			System.out.println("running "+  method + " w/ alpha: " + alpha);
		}
		
		// output
		String outputDir = "/Users/christanner/research/projects/CitationFinder/eval/";
		
		//String scoresFile = outputDir + "scores_" + method + "_" + corpus + ".txt";
		String topicStats = outputDir + "topicStats_" + method + "_" + corpus + ".txt";
		CitationEngine ce = null;
		if (method.equals("lda")) {
			ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, ldaInput, topicStats, sourcesCutoffs, malletInputFile);
		} else if (method.equals("plsa")) {
			ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, plsaInput,topicStats, sourcesCutoffs, malletInputFile);			
		} else if (method.equals("linkLDA")) {
			ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, linkLDAInput,topicStats, sourcesCutoffs, malletInputFile);
		} else if (method.equals("authorLinkLDA1")) {
			ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, authorLinkLDA1Input, topicStats, sourcesCutoffs, malletInputFile);	
		} else if (method.equals("authorLinkLDA2")) {
			ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, authorLinkLDA2Input, topicStats, sourcesCutoffs, malletInputFile);	
		}  else if (method.equals("authorLinkLDA3")) {
			//ce = new CitationEngine(alpha, scoringMethod, docsPath, trainingGoldFile, testingGoldFile, aclMetaFile, authorLinkLDA3Input, scoresFile, topicStats, sourcesCutoffs, malletInputFile);	
		}
 		List<Double> recallResults = ce.getRecallResults(outputDir, false);
		
 		//ce.getCitedStats(outputDir, 10); // 10 = numbins; this is just a temp. test to validate that 'prevCited' actually gives us help
		//System.exit(1);
		ce.printAllGraphs(outputDir, corpus, method);
		
		if (method.equals("linkLDA")) {
			ce.printLinkLDASpecifics(outputDir + "specifics_" + method + ".txt");
		}
		//List<Double> recallResults = ce.getLDARecallResults("P05-1022", outputDir, true);
		System.out.println("recall: " + recallResults);
		System.out.println("positi: " + sourcesCutoffs);
		
		// prints in x,y format for making a graph
		for (int i=0; i<sourcesCutoffs.size(); i++) {
			if (sourcesCutoffs.get(i) <= recallResults.size()) {
				System.out.println(recallResults.get(sourcesCutoffs.get(i)));
			}
		}
		
		// writes report for "P08-2011" -- 
		//id = {P08-2011}
		//author = {Elsner, Micha; Charniak, Eugene}
		//title = {Coreference-inspired Coherence Modeling}
		//venue = {Annual Meeting Of The Association For Computational Linguistics}
		//year = {2008}
		//System.out.println(ce.writeTopicModelResults("P08-2011", outputDir, true));
		
		
		// predicts which sentences cited the docs
		//List<Integer> ranks = ce.writeSentencePredictions("P05-1022", outputDir, true);
		//System.out.println(ranks);
	}

}

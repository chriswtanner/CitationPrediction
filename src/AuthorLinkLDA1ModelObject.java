import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class AuthorLinkLDA1ModelObject implements Serializable {

	Map<String, Map<Integer, Map<String, Double>>> authorTopicToLinkProbs = new HashMap<String, Map<Integer, Map<String, Double>>>();
	Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
	Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
	
	Map<String, Integer> linkToID = new HashMap<String, Integer>();
	Map<Integer, String> IDToLink = new HashMap<Integer, String>();
	
	public AuthorLinkLDA1ModelObject(Map<String, Integer> w, Map<Integer, String> i, 
			Map<String, Map<Integer, Map<String, Double>>> l, Map<Integer, Map<String, Double>> t, Map<String, Double[]> d) {
		this.authorTopicToLinkProbs = l;
		this.topicToWordProbabilities = t;
		this.docToTopicProbabilities = d;
		
		this.linkToID = w;
		this.IDToLink = i;
	}

}

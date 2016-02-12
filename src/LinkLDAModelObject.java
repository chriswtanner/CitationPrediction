import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class LinkLDAModelObject implements Serializable {

	Map<Integer, Map<String, Double>> topicToLinkProbabilities = new HashMap<Integer, Map<String, Double>>();
	Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
	Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
	
	Map<String, Integer> linkToID = new HashMap<String, Integer>();
	Map<Integer, String> IDToLink = new HashMap<Integer, String>();
	
	public LinkLDAModelObject(Map<String, Integer> w, Map<Integer, String> i, 
			Map<Integer, Map<String, Double>> l, Map<Integer, Map<String, Double>> t, Map<String, Double[]> d) {
		this.topicToLinkProbabilities = l;
		this.topicToWordProbabilities = t;
		this.docToTopicProbabilities = d;
		
		this.linkToID = w;
		this.IDToLink = i;
	}

}

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class TopicModelObject implements Serializable {

	Map<Integer, Map<String, Double>> topicToWordProbabilities = new HashMap<Integer, Map<String, Double>>();
	Map<String, Double[]> docToTopicProbabilities = new HashMap<String, Double[]>();
	
	Map<String, Integer> wordToID = new HashMap<String, Integer>();
	Map<Integer, String> IDToWord = new HashMap<Integer, String>();
	
	public TopicModelObject(Map<String, Integer> w, Map<Integer, String> i,
			Map<Integer, Map<String, Double>> t, Map<String, Double[]> d) {
		this.topicToWordProbabilities = t;
		this.docToTopicProbabilities = d;
		this.wordToID = w;
		this.IDToWord = i;
	}

}

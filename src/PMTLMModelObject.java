import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class PMTLMModelObject implements Serializable {

	Map<String, Map<String, Double>> docToDocProbabilities = new HashMap<String, Map<String, Double>>();
	
	public PMTLMModelObject(Map<String, Map<String, Double>> d) {
		this.docToDocProbabilities = d;
	}
}

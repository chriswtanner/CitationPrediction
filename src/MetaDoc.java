import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class MetaDoc {
	String docID = "";
	String year = "";
	String title = "";
	List<String> names = new ArrayList<String>();

	public MetaDoc(String docID, String title, String year, List<String> names) {
		this.docID = docID;
		this.title = title;
		this.year = year;
		this.names = names;
	}
	public String toString() { return docID + ";" + year + "; names:" + names; }
}

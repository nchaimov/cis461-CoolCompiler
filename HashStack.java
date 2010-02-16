import java.util.HashMap;
import java.util.Stack;
import java.util.Map.Entry;

public class HashStack<K, V> {

	protected class Mapping {
		public K key;
		public V value;

		public Mapping(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}

	protected HashMap<K, V> map = new HashMap<K, V>();

	protected Stack<Mapping> undoStack = new Stack<Mapping>();

	public void push(K key, V value) {
		V oldValue = map.get(key);
		undoStack.push(new Mapping(key, oldValue));
		map.put(key, value);
	}

	public void pop() {
		Mapping m = undoStack.pop();
		map.put(m.key, m.value);
	}

	public V get(K key) {
		return map.get(key);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{  ");
		for (Entry<K, V> entry : map.entrySet()) {
			if (entry.getValue() != null) {
				sb.append("").append(entry.getKey()).append(" => ").append(entry.getValue())
						.append(", ");
			}
		}
		sb.append("\b\b}");
		return sb.toString();

	}

}

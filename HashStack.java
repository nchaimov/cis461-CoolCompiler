import java.util.HashMap;
import java.util.Stack;
import java.util.Map.Entry;

public class HashStack<K, V> {
	
	protected class Mapping {
		public K key;
		public V value;
		
		public Mapping(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}
	
	protected HashMap<K, V> map = new HashMap<K, V>();
	
	protected Stack<Mapping> undoStack = new Stack<Mapping>();
	
	public void push(final K key, final V value) {
		final V oldValue = map.get(key);
		undoStack.push(new Mapping(key, oldValue));
		map.put(key, value);
	}
	
	public void pop() {
		final Mapping m = undoStack.pop();
		map.put(m.key, m.value);
	}
	
	public V get(final K key) {
		return map.get(key);
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("{  ");
		for (final Entry<K, V> entry : map.entrySet()) {
			if (entry.getValue() != null) {
				sb.append("").append(entry.getKey()).append(" => ").append(
						entry.getValue()).append(", ");
			}
		}
		sb.append("\b\b}");
		return sb.toString();
		
	}
	
}

import java.util.HashMap;
import java.util.Stack;

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

}

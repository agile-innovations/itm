package cc.mallet.topics.tree;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;

public class HIntIntObjectHashMap<V> {
	TIntObjectHashMap<TIntObjectHashMap<V>> data;
	
	public HIntIntObjectHashMap () {
		this.data = new TIntObjectHashMap<TIntObjectHashMap<V>>();
	}
	
	public void put(int key1, int key2, V value) {
		if(! this.data.contains(key1)) {
			this.data.put(key1, new TIntObjectHashMap<V>());
		}
		TIntObjectHashMap<V> tmp = this.data.get(key1);
		tmp.put(key2, value);
	}
	
	public V get(int key1, int key2) {
		if (this.contains(key1, key2)) {
			return this.data.get(key1).get(key2);
		} else {
			System.out.println("HIntIntObjectHashMap: key does not exist! " + key1 + " " + key2);
			return null;
		}
	}
	
	public TIntObjectHashMap<V> get(int key1) {
		return this.data.get(key1);
	}
	
	public int[] getKey1Set() {
		return this.data.keys();
	}
	
	public boolean contains(int key1, int key2) {
		if (this.data.contains(key1)) {
			return this.data.get(key1).contains(key2);
		} else {
			return false;
		}
	}
}

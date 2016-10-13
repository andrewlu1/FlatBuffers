package cn.andrewlu.test;

/**
 * Field Type support :1. Primitive Class such as int,Integer,byte,Byte ; 2. CharSequence ; 3. ? extends TableEx. 
 * Array support: 4. Collection such as List,Set; 5. Array[] such as int[], Byte[]. but it cannot be announced like this:List<Byte[]>.
 * Make sure you have all field you want serialized added @Index() on it.
 * Make sure you have @Index(type=?)  annontion with Collection Field cause i cannot get the element type automatic at runtime.
 * Make sure you have two constructors in all the classes that extends TableEx.  
 */
import java.nio.ByteBuffer;

import com.google.flatbuffers.Index;
import com.google.flatbuffers.FBTable;

public class TestModel extends FBTable {
	@Index(id = 0)
	int[] data;

	public TestModel() {
	}

	public TestModel(ByteBuffer bf) {
		super(bf);
	}
}

class SubModel extends FBTable {
	@Index(id = 0)
	public float i;

	public SubModel() {
	}

	public SubModel(float i) {
		this.i = i;
	}

	public SubModel(ByteBuffer bf) {
		super(bf);
	}
}
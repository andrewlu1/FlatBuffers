package cn.andrewlu.test;

/**
 * Field Type support :1. Primitive Class such as int,Integer,byte,Byte ; 2. CharSequence ; 3. ? extends TableEx. 
 * Array support: 4. Collection such as List,Set; 5. Array[] such as int[], Byte[]. but it cannot be announced like this:List<Byte[]>.
 * Make sure you have all field you want serialized added @Index() on it.
 * Make sure you have @Index(type=?)  annontion with Collection Field cause i cannot get the element type automatic at runtime.
 * Make sure you have two constructors in all the classes that extends TableEx.  
 */
import java.nio.ByteBuffer;
import java.util.List;

import com.google.flatbuffers.Index;
import com.google.flatbuffers.TableEx;

public class TestModel extends TableEx {

	@Index(id = 1)
	public String strField;

	@Index(id = 0, type = String.class)
	public List<String> arrayField;

	@Index(id = 2)
	public int[] intArrayField;

	@Index(id = 3)
	public SubModel objField;

	public TestModel() {
	}

	public TestModel(ByteBuffer bf) {
		super(bf);
	}
}

class SubModel extends TableEx {
	@Index(id = 0)
	public float data;

	public SubModel() {
	}

	public SubModel(ByteBuffer bf) {
		super(bf);
	}
}
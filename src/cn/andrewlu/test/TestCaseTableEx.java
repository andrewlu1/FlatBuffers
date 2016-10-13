package cn.andrewlu.test;

import java.nio.ByteBuffer;
import java.util.List;

import com.google.flatbuffers.FBTable;

public class TestCaseTableEx {

	public static void main(String[] args) {

		TestModel model = new TestModel();
		// model.data = new SubModel[] { new SubModel(1), new SubModel(2) };
		model.data = new int[] { 23, 546 };
		// encode data to byte[].
		ByteBuffer buffer = model.encode();

		// decode data from byte[].
		TestModel decodeModel = new TestModel(buffer);
		System.out.println(decodeModel);

		List<Integer> models = FBTable.decode(buffer, int.class);
		ByteBuffer buffer2 = FBTable.encode(models);
		models = FBTable.decode(buffer2, int.class);
		System.out.println(models);
		System.out.println(buffer2);
	}

}

package cn.andrewlu.test;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TestCaseTableEx {

	public static void main(String[] args) {
		
		TestModel model = new TestModel();
		model.objField = new SubModel();
		model.intArrayField = new int[] { 1, 2, 3, 4, 5 };
		model.strField = "Hello,FlatBuffer";
		model.arrayField = new ArrayList<String>();

		// encode data to byte[].
		ByteBuffer buffer = model.encode();

		// decode data from byte[].
		TestModel decodeModel = new TestModel(buffer);
		System.out.println(decodeModel);
	}

}

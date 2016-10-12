# FlatBuffers
改进Java 版本的FlatBuffers 操作，使其兼容JavaBean操作规范。
普通的FlatBuffers 模型存在以下几个问题：
1. 构造创建一个模型对象很复杂，要事先创建其各个属性对象，然后才能开始模型本身的创建。
2. 模型不符合JavaBean规范，没有真正的属性域，当需要以JavaBean规范操作模型时比如给属性字段增加注解等操作时只能舍弃FlatBuffer。
3. 在纯Java开发中（前端和后台都以Java开发），要维护FlatBuffers模型需要以下几步：1.修改schema, 2.flatc编译出java文件，3.分别替换前端各后台的各模型文件。
这是非常不友好的操作。非常希望仅维护一份公共的Java模型包，而对FlatBuffer操作透明。

基于以上问题，特改写FlatBuffers 的Table类型，使其支持通用JavaBean对象的编码和解码能力。
改写后的使用类似以下片段：
	
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
    
TestModel 的声明如下：
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

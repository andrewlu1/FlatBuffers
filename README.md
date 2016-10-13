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

        public class TestModel extends FBTable {
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
	
使用：
只需包含src中的代码到自己的工程即可拥有FlatBuffers 的编解码能力，不需要再编写schema 和flatc 编译schema. 
就像编写普通的JavaBean模型一样编写自己的类，只需继承FBTable类，并按昭示例代码的样式为属性添加 @Index索引注解即可。
这样模型便具有了encode()和decode()两个基础能力。

待新增能力：
//直接解析根数据为模型数组的情形。即ByteBuffer中直接存储模型数组的情况。当前只能解析ByteBuffer中存在单个模型对象的情形。

V2 在FBTable中增加了对列表数据的编码和解析过程。
使用过程如下：

	List<Integer> models = FBTable.decode(buffer, int.class);
	ByteBuffer buffer2 = FBTable.encode(models);

以上两个过程应当是可逆的。即有了列表数据ByteBuffer便可以解析出相应的列表对象，同样将列表对象传入encode中又能够恢复出ByteBuffer
实现过程相应比较简单，可以参考接口实现。

有关FlatBuffers 详细信息参考官方文档：http://google.github.io/flatbuffers/
有任何问题请联系作者：andrewlu1@126.com


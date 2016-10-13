package com.google.flatbuffers;

/**
 * 封装Table类型，使普通JavaBean类型能够方便与FB接入。
 */

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class FBTable extends Table {
	protected final static int INVALIDE = -1;

	public FBTable(ByteBuffer _bb) {
		_bb.order(ByteOrder.LITTLE_ENDIAN);
		bb_pos = _bb.getInt(_bb.position()) + _bb.position();
		bb = _bb;
		decode();
	}

	public FBTable() {
	}

	// 只给自己用，用来指定List元素的类型。
	private FBTable(ByteBuffer _bb, Class dataElementType) {
		_bb.order(ByteOrder.LITTLE_ENDIAN);
		bb_pos = _bb.getInt(_bb.position()) + _bb.position();
		bb = _bb;
		decode(dataElementType);
	}

	// 这可能涉及到递归调用。因此需要注意调用顺序问题。
	public int encode(FlatBufferBuilder builder) {
		Field[] fields = getClass().getDeclaredFields();
		// 获取待序列化属性数量。
		List<Field> serialableFields = new ArrayList<Field>(fields.length);
		TreeSet<Integer> ids = new TreeSet<Integer>();
		for (Field f : fields) {
			Index index = f.getAnnotation(Index.class);
			if (index == null)
				continue;
			assertDie(ids.add(index.id()), "@Index must not be duplicate,id:"
					+ index.id());
			serialableFields.add(f);
		}
		int count = serialableFields.size();
		if (count <= 0)
			return INVALIDE;
		// ID 必须从0开始，按顺序递增，不能跳跃
		boolean corrct = 0 == ids.first() && count == ids.size()
				&& count == ids.last() + 1;
		assertDie(
				corrct,
				"@Index must Comply with the contract,see the flatbuffer doc for more info about schema id.");

		HashMap<Field, Object> fieldValueMap = new HashMap<Field, Object>(count);
		// 需要先创建所有的field.然后才能开始自己的创建。Field 创建后根据类型会返回本值或者其索引信息
		for (Field f : serialableFields) {
			fieldValueMap.put(f, encodeField(builder, f));
		}

		builder.startObject(count);

		// 填充自己的属性内容。
		Set<Field> iterator = fieldValueMap.keySet();
		for (Field f : iterator) {
			Index index = f.getAnnotation(Index.class);
			Object value = fieldValueMap.get(f);
			if (index == null || value == null)
				continue;
			ClassType type = getClassType(f.getType());
			switch (type) {
			case Primitive:// 返回的是field 值本身。
				addPrimitiveValue(builder, fieldValueMap.get(f), index.id());
				break;
			case Array:
			case List:
			case CharSequence:
			case FBTable:
			case ByteVector:
				builder.addOffset(index.id(), ((Integer) value).intValue(), 0);
				break;
			}
		}
		return builder.endObject();
	}

	public ByteBuffer encode() {
		FlatBufferBuilder builder = new FlatBufferBuilder();
		int inst = encode(builder);
		if (inst != INVALIDE) {
			builder.finish(inst);
		}
		return builder.dataBuffer();
	}

	// 处理数组类型
	private final static List LIST_EMPTY = new ArrayList(0);

	public static <T> List<T> decode(ByteBuffer listDataBuffer, Class<T> c) {
		LIST_EMPTY.clear();// 防止外界对其进行修改后影响其他对象解析。
		if (listDataBuffer == null) {
			return LIST_EMPTY;
		}
		assertDie(checkTypeValid(c),
				"Encode Type cannot be null or outside class of TableEx!");
		List<T> data = new TableArrayParser<T>(listDataBuffer, c).getData();
		if (data == null)
			return LIST_EMPTY;
		return data;
	}

	// 将列表数据编码。
	public static ByteBuffer encode(List objList) {
		TableArrayParser parser = new TableArrayParser(objList);
		return parser.encode();
	}

	public FBTable decode() {
		decode(null);
		return this;
	}

	// 通过遍历头上的ID索引而确定其值。
	private void decode(Class listElementType) {
		try {
			Class c = this.getClass();
			Field[] fields = c.getDeclaredFields();
			for (Field f : fields) {
				Index index = f.getAnnotation(Index.class);
				if (index == null)
					continue;
				int ident = f.getModifiers();
				// final or static field donot serialized.
				if (Modifier.isFinal(ident) || Modifier.isStatic(ident))
					continue;
				Class type = f.getType();

				int id = index.id();
				Class actualType = null;

				// 数组类型。
				if (type.isArray()) {
					actualType = type.getComponentType();
				} else if (Collection.class.isAssignableFrom(type)) {
					// 当传入的元素类型不为空时，以此为准备，否则读取注解中的类型。这里是为了解决数组类型的对象解析做铺垫。
					if (listElementType != null) {
						actualType = listElementType;
					} else {
						actualType = index.type();
						String clzName = index.typeStr();
						if (Object.class == actualType) {
							if (!"".equals(clzName)) {
								actualType = Class.forName(clzName);
							}
						}
					}
				} else if (checkTypeValid(type)) {
					// 读取普通对象。
					decodePrimitiveField(f, id);
					continue;
				} else
					continue;

				assertDie(
						checkTypeValid(actualType),
						String.format(
								" Field:%s of Class:%s is ArrayType, please check "
										+ "if you'v set @Index(type=?) Annotation on it,this is truely necessary.",
								f.getName(), getClass().getName()));

				// 读取数组对象。
				decodeArrayField(f, id, actualType);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * 检测目标类型是否符合序列化类型。
	 * 
	 * @param t
	 * @return
	 */
	private static boolean checkTypeValid(Class t) {
		return t != null
				&& (FBTable.class.isAssignableFrom(t) || isPrimitiveType(t) || CharSequence.class
						.isAssignableFrom(t));
	}

	/**
	 * 检测目标类型是否为基础类型或者装箱基本类型。
	 * 
	 * @param t
	 * @return
	 */
	private static boolean isPrimitiveType(Class t) {
		return primitiveClassIndexOf(t) >= 0;
	}

	// 普通类型属性的赋值操作。
	private void decodePrimitiveField(Field f, int id) {
		Class c = f.getType();
		f.setAccessible(true);
		int o = __offset(4 + id * 2);// 属性在内容区的偏移
		try {
			int classType = primitiveClassIndexOf(c);
			if (classType >= 0) {
				// 基础类型操作
				switch (classType) {
				case 0:
					f.setBoolean(this, o != 0 ? 0 != bb.get(o + bb_pos) : false);
					break;
				case 1:
					f.set(this, o != 0 ? 0 != bb.get(o + bb_pos) : false);
					break;
				case 2:
					f.setByte(this, o != 0 ? bb.get(o + bb_pos) : 0);
					break;
				case 3:
					f.set(this, o != 0 ? bb.get(o + bb_pos) : 0);
					break;
				case 4:
					f.setShort(this, o != 0 ? bb.getShort(o + bb_pos) : 0);
					break;
				case 5:
					f.set(this, o != 0 ? bb.getShort(o + bb_pos) : 0);
					break;
				case 6:
					f.setChar(this, o != 0 ? bb.getChar(o + bb_pos) : '\0');
					break;
				case 7:
					f.set(this, o != 0 ? bb.getChar(o + bb_pos) : '\0');
					break;
				case 8:
					f.setInt(this, o != 0 ? bb.getInt(o + bb_pos) : 0);
					break;
				case 9:
					f.set(this, o != 0 ? bb.getInt(o + bb_pos) : 0);
					break;
				case 10:
					f.setFloat(this, o != 0 ? bb.getFloat(o + bb_pos) : 0);
					break;
				case 11:
					f.set(this, o != 0 ? bb.getFloat(o + bb_pos) : 0);
					break;
				case 12:
					f.setLong(this, o != 0 ? bb.getLong(o + bb_pos) : 0);
					break;
				case 13:
					f.set(this, o != 0 ? bb.getLong(o + bb_pos) : 0);
					break;
				case 14:
					f.setDouble(this, o != 0 ? bb.getDouble(o + bb_pos) : 0);
					break;
				case 15:
					f.set(this, o != 0 ? bb.getDouble(o + bb_pos) : 0);
					break;
				}
			} else if (FBTable.class.isAssignableFrom(c)) {
				if (o == 0)
					return;
				// 嵌套对象操作
				Constructor constructor = c.getConstructor((Class[]) null);
				constructor.setAccessible(true);
				FBTable obj = (FBTable) constructor
						.newInstance((Object[]) null);
				obj.bb_pos = __indirect(o + bb_pos);
				obj.bb = bb;
				obj.decode();
				f.set(this, obj);
			} else if (CharSequence.class.isAssignableFrom(c)) {
				// 字符串操作
				if (o == 0)
					return;
				f.set(this, __string(o + bb_pos));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 数组类型的赋值。
	private void decodeArrayField(Field f, int id, Class elemType) {
		Class c = f.getType();
		f.setAccessible(true);
		int o = __offset(4 + id * 2);// 属性在内容区的偏移
		if (o == 0)
			return;

		int length = __vector_len(o);
		if (length <= 0)
			return;

		// 这里基础类型与其他类型创建方式不一样。
		try {
			Object value = null;
			if (c.isArray()) {
				value = Array.newInstance(elemType, length);
			} else if (Collection.class.isAssignableFrom(c)) {
				value = createCollection(c, length);
			} else
				return;

			f.set(this, value);
			fillArrays(value, o, elemType);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Collection createCollection(Class collectionType, int cap) {
		int modifier = collectionType.getModifiers();
		if (Modifier.isInterface(modifier)) {// 接口类型
			if (List.class.isAssignableFrom(collectionType)) {
				return new ArrayList(cap);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return new HashSet(cap);
			} else if (Collection.class.isAssignableFrom(collectionType)) {
				return new ArrayList(cap);
			}
		}
		if (!Modifier.isAbstract(modifier)) {// 实际子类
			Constructor constructor = null;
			try {
				constructor = collectionType.getConstructor(int.class);
				constructor.setAccessible(true);
				return (Collection) constructor.newInstance(cap);
			} catch (Exception e) {
			}
			try {
				constructor = collectionType.getConstructor((Class[]) null);
				constructor.setAccessible(true);
				return (Collection) constructor.newInstance();
			} catch (Exception e) {
			}
		} else {
			if (List.class.isAssignableFrom(collectionType)) {
				return new ArrayList(cap);
			} else if (Set.class.isAssignableFrom(collectionType)) {
				return new HashSet(cap);
			} else if (Collection.class.isAssignableFrom(collectionType)) {
				return new ArrayList(cap);
			}
		}
		return new ArrayList(cap);
	}

	/**
	 * 填充数组或列表类属性值。
	 * 
	 * @param arrayOrList
	 * @param offset
	 * @param elemType
	 */
	private void fillArrays(Object arrayOrList, int offset, Class elemType) {
		int size = __vector_len(offset);
		int o = offset;
		try {
			ClassType type = getClassType(elemType);
			// 不同的数据类型占不同的字节数。
			int depth = dataWidthOfClass(elemType);
			for (int i = 0; i < size; i++) {
				Object ele = null;
				o = __vector(offset) + i * depth;
				switch (type) {
				case Primitive: {
					// 基础类型操作
					if (int.class == elemType || Integer.class == elemType) {
						ele = bb.getInt(o);
					} else if (short.class == elemType
							|| Short.class == elemType) {
						ele = bb.getShort(o);
					} else if (byte.class == elemType || Byte.class == elemType) {
						ele = bb.get(o);
					} else if (long.class == elemType || Long.class == elemType) {
						ele = bb.getLong(o);
					} else if (float.class == elemType
							|| Float.class == elemType) {
						ele = bb.getFloat(o);
					} else if (double.class == elemType
							|| Double.class == elemType) {
						ele = bb.getDouble(o);
					} else if (char.class == elemType
							|| Character.class == elemType) {
						ele = bb.getChar(o);
					} else if (boolean.class == elemType
							|| Boolean.class == elemType) {
						ele = 0 != bb.get(o);
					}
					break;
				}
				case FBTable: {
					// 嵌套对象操作
					Constructor constructor = elemType
							.getConstructor((Class[]) null);
					constructor.setAccessible(true);
					FBTable obj = (FBTable) constructor
							.newInstance((Object[]) null);
					obj.bb_pos = __indirect(o);
					obj.bb = bb;
					obj.decode();
					ele = obj;
					break;
				}
				case CharSequence: {
					// 字符串操作
					ele = __string(o);
					break;
				}
				}

				if (arrayOrList instanceof Collection) {
					((Collection) arrayOrList).add(ele);
				} else {
					Array.set(arrayOrList, i, ele);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private enum ClassType {
		Primitive, FBTable, CharSequence, Array, ByteVector, List, Unknown
	}

	// 区分类型。
	private ClassType getClassType(Class elemType) {
		ClassType type = ClassType.Unknown;
		if (isPrimitiveType(elemType)) {
			type = ClassType.Primitive;
		} else if (FBTable.class.isAssignableFrom(elemType)) {
			type = ClassType.FBTable;
		} else if (CharSequence.class.isAssignableFrom(elemType)) {
			type = ClassType.CharSequence;
		} else if (elemType.isArray()) {
			type = ClassType.Array;
			Class actuType = elemType.getComponentType();
			if (byte.class == actuType || Byte.class == actuType) {
				type = ClassType.ByteVector;
			}
		} else if (Collection.class.isAssignableFrom(elemType)) {
			type = ClassType.List;
		}
		return type;
	}

	private final static Class[] PrimitiveClasses = new Class[] {
			boolean.class, Boolean.class,/* 0,1 */
			byte.class, Byte.class, /* 2,3 */
			short.class, Short.class, /* 4,5 */
			char.class, Character.class, /* 6,7 */
			int.class, Integer.class, /* 8,9 */
			float.class, Float.class, /* 10,11 */
			long.class, Long.class, /* 12,13 */
			double.class, Double.class /* 14,15 */
	};

	/**
	 * 判断是否为标准类型，并返回其索引。否则返回-1.
	 * 
	 * @param c
	 * @return
	 */
	private static int primitiveClassIndexOf(Class c) {
		// key 与index 对应.
		int size = PrimitiveClasses.length;
		for (int i = 0; i < size; i++)
			if (PrimitiveClasses[i] == c)
				return i;
		return -1;
	}

	/**
	 * 判断类型宽度。
	 * 
	 * @param c
	 * @return 1, 2, 4, 8.
	 */
	private static int dataWidthOfClass(Class c) {
		int type = primitiveClassIndexOf(c);
		switch (type) {
		case -1:
			return 4;// 非基础类型
		case 0:
		case 1:
		case 2:
		case 3:
			return 1;
		case 4:
		case 5:
		case 6:
		case 7:
			return 2;
		case 8:
		case 9:
		case 10:
		case 11:
			return 4;
		case 12:
		case 13:
		case 14:
		case 15:
			return 8;
		default:
			return 4;
		}
	}

	/**
	 * 构建Field对就的索引。如果Field为空，则返回null, 不参与编码。
	 * 
	 * @param builder
	 * @param field
	 * @return
	 */
	private Object encodeField(final FlatBufferBuilder builder, Field field) {
		Index index = field.getAnnotation(Index.class);

		ClassType type = getClassType(field.getType());
		field.setAccessible(true);
		try {
			Object o = field.get(this);
			if (o == null)
				return null;
			switch (type) {
			case Primitive:// 返回field value.
				return o;
			case CharSequence:
				return builder.createString((CharSequence) o);
			case FBTable:
				// 如果属性值为空，则不进行序列化
				return ((FBTable) o).encode(builder);
			case ByteVector:
				if (Byte[].class == field.getType()) {
					return builder.createByteVector(toPrimitive((Byte[]) o));
				}
				return builder.createByteVector((byte[]) o);
			case Array: {
				int size = Array.getLength(o);
				if (size <= 0)
					return null;
				Class c = field.getType().getComponentType();
				return encodeArrayData(builder, o, size, c);
			}
			case List:// 最终都会转换成int[] 列表。
			{
				int size = ((Collection) o).size();
				if (size <= 0)
					return null;
				Class c = index.type();
				String clzName = index.typeStr();
				if (Object.class == c) {
					if ("".equals(clzName)) {
						// 如果什么类型都没设置，则检测元素对象的类型。
						Iterator iterator = ((Collection) o).iterator();
						do {
							if (iterator.hasNext()) {
								Object eleObject = iterator.next();
								if (eleObject != null) {
									c = eleObject.getClass();
									break;
								}
							}
							assertDie(false,
									"you must set type of element on the List field.");
						} while (false);

					} else {
						c = Class.forName(clzName);
					}
				}
				return encodeArrayData(builder, o, size, c);
			}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// 如果是基本类型，则不需要提前创建，直接在对象中addXXX(). 否则就需要先创建后addOffset.
	private Object encodeArrayData(final FlatBufferBuilder builder,
			Object arrayOrList, int size, Class elemType) {
		final ClassType type = getClassType(elemType);
		int classDepth = dataWidthOfClass(elemType);
		assertDie(ClassType.Unknown != type,
				"Array Element is Unknown type.make sure you have @Index(type=?) on it.");
		if (ClassType.Primitive == type) {

			builder.startVector(classDepth, size, classDepth);
			forEach(arrayOrList, true, new ElementListener() {
				public void accept(Object ele, int index) {
					addPrimitiveValue(builder, ele);
				}
			});
			return builder.endVector();
		} else if (ClassType.FBTable == type || ClassType.CharSequence == type) {
			// 用来保存列表元素的索引。正序保存，但反序添加。
			final int[] offsets = new int[size];
			forEach(arrayOrList, false, new ElementListener() {
				public void accept(Object ele, int index) {
					if (ClassType.FBTable == type) {
						offsets[index] = ((FBTable) ele).encode(builder);
					} else {
						offsets[index] = builder
								.createString((CharSequence) ele);
					}
				}
			});

			builder.startVector(classDepth, size, classDepth);
			for (int i = size - 1; i >= 0; i--) {
				builder.addOffset(offsets[i]);
			}
			return builder.endVector();
		}
		return null;
	}

	// 转换数组类型。
	private static byte[] toPrimitive(Byte[] array) {
		if (array == null) {
			return null;
		}
		if (array.length == 0) {
			return null;
		}
		byte[] result = new byte[array.length];
		for (int i = 0; i < array.length; i++) {
			result[i] = array[i].byteValue();
		}
		return result;
	}

	// 向FB中增加基本类型数据。
	private void addPrimitiveValue(FlatBufferBuilder builder, Object value,
			int index) {
		if (value == null)
			return;
		int classType = primitiveClassIndexOf(value.getClass());
		switch (classType) {
		case 0:
		case 1:
			builder.addBoolean(index, (Boolean) value, false);
			break;
		case 2:
		case 3:
			builder.addByte(index, (Byte) value, 0);
			break;
		case 4:
		case 5:
			builder.addShort(index, (Short) value, 0);
			break;
		case 6:
		case 7:
			builder.addShort(index,
					(short) (((Character) value).charValue() + 0), 0);
			break;
		case 8:
		case 9:
			builder.addInt(index, (Integer) value, 0);
			break;
		case 10:
		case 11:
			builder.addFloat(index, (Float) value, 0);
			break;
		case 12:
		case 13:
			builder.addLong(index, (Long) value, 0);
			break;
		case 14:
		case 15:
			builder.addDouble(index, (Double) value, 0);
			break;
		}
	}

	// 不指定索引向FB中添加基本类型数据。
	private void addPrimitiveValue(FlatBufferBuilder builder, Object value) {
		if (value == null)
			return;
		int classType = primitiveClassIndexOf(value.getClass());
		switch (classType) {
		case 0:
		case 1:
			builder.addBoolean((Boolean) value);
			break;
		case 2:
		case 3:
			builder.addByte((Byte) value);
			break;
		case 4:
		case 5:
			builder.addShort((Short) value);
			break;
		case 6:
		case 7:
			builder.addShort((short) (((Character) value).charValue() + 0));
			break;
		case 8:
		case 9:
			builder.addInt((Integer) value);
			break;
		case 10:
		case 11:
			builder.addFloat((Float) value);
			break;
		case 12:
		case 13:
			builder.addLong((Long) value);
			break;
		case 14:
		case 15:
			builder.addDouble((Double) value);
			break;
		}
	}

	// 遍历列表或数组类对象
	private void forEach(Object arrayOrList, boolean reversed, ElementListener r) {
		if (r == null)
			return;
		ClassType objClass = getClassType(arrayOrList.getClass());
		Object elemObject = null;
		int index = 0;
		if (ClassType.Array == objClass) {
			int size = Array.getLength(arrayOrList);
			for (int i = 0; i < size; i++) {
				index = i;
				if (reversed) {
					index = size - i - 1;
				}
				elemObject = Array.get(arrayOrList, index);
				r.accept(elemObject, index);
			}
		} else if (ClassType.List == objClass) {
			Object[] dataObjects = ((Collection) arrayOrList).toArray();
			int size = dataObjects.length;
			for (int i = 0; i < size; i++) {

				index = i;
				if (reversed) {
					index = size - i - 1;
				}

				elemObject = dataObjects[index];
				r.accept(elemObject, index);
			}
		} else {
			assertDie(false, "invalidate array object parsed");
		}
	}

	private interface ElementListener {
		public void accept(Object ele, int index);
	}

	// 不满足条件就报错。
	private static void assertDie(boolean exp, String msg) {
		if (!exp) {
			throw new RuntimeException(msg);
		}
	};

	private final static class TableArrayParser<T> extends FBTable {
		@Index(id = 0)
		public List<T> data;

		public TableArrayParser(List<T> data) {
			this.data = data;
		}

		public TableArrayParser(ByteBuffer bf, Class<T> dClass) {
			super(bf, dClass);
		}

		public final List<T> getData() {
			return data;
		}
	}
}

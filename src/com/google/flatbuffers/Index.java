package com.google.flatbuffers;

/**
 * 用于Models 的属性索引顺序标记。无标记将不参与序列化。
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Index {
	// 索引顺序，不能重复
	int id();

	// 属性类型，只有List才需要标记元素类型（无法获取List泛型类型）
	Class type() default Object.class;

	// 与type类似，如果type
	String typeStr() default "";
}

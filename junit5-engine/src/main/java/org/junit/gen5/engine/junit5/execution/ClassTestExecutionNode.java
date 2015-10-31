/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.execution;

import static org.junit.gen5.commons.util.AnnotationUtils.*;
import static org.junit.gen5.commons.util.ReflectionUtils.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.gen5.api.AfterAll;
import org.junit.gen5.api.BeforeAll;
import org.junit.gen5.commons.util.ReflectionUtils;
import org.junit.gen5.engine.EngineExecutionContext;
import org.junit.gen5.engine.junit5.descriptor.ClassTestDescriptor;

/**
 * @author Stefan Bechtold
 * @author Sam Brannen
 * @since 5.0
 */
class ClassTestExecutionNode extends TestExecutionNode {

	static final String TEST_INSTANCE_ATTRIBUTE_NAME = ClassTestExecutionNode.class.getName() + ".TestInstance";

	private final ClassTestDescriptor testDescriptor;

	ClassTestExecutionNode(ClassTestDescriptor testDescriptor) {
		this.testDescriptor = testDescriptor;
	}

	private Object createTestInstance() {
		try {
			return ReflectionUtils.newInstance(getTestDescriptor().getTestClass());
		}
		catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException ex) {
			throw new IllegalStateException(
				String.format("Test %s is not well-formed and cannot be executed", getTestDescriptor().getUniqueId()),
				ex);
		}
	}

	@Override
	public ClassTestDescriptor getTestDescriptor() {
		return this.testDescriptor;
	}

	@Override
	public void execute(EngineExecutionContext context) {
		Class<?> testClass = getTestDescriptor().getTestClass();
		Object testInstance = createTestInstance();
		context.getAttributes().put(TEST_INSTANCE_ATTRIBUTE_NAME, testInstance);

		try {
			executeBeforeAllMethods(testClass, testInstance);
			for (TestExecutionNode child : getChildren()) {
				child.execute(context);
			}
		}
		catch (Exception e) {
			context.getTestExecutionListener().testFailed(getTestDescriptor(), e);
		}
		finally {
			try {
				executeAfterAllMethods(context, testClass, testInstance);
			}
			finally {
				context.getAttributes().remove(TEST_INSTANCE_ATTRIBUTE_NAME);
			}
		}
	}

	private void executeBeforeAllMethods(Class<?> testClass, Object testInstance) throws Exception {
		for (Method method : findAnnotatedMethods(testClass, BeforeAll.class, MethodSortOrder.HierarchyDown)) {
			invokeMethod(method, testInstance);
		}
	}

	private void executeAfterAllMethods(EngineExecutionContext context, Class<?> testClass, Object testInstance) {
		Exception exceptionDuringAfterAll = null;

		for (Method method : findAnnotatedMethods(testClass, AfterAll.class, MethodSortOrder.HierarchyUp)) {
			try {
				invokeMethod(method, testInstance);
			}
			catch (Exception e) {
				if (exceptionDuringAfterAll == null) {
					exceptionDuringAfterAll = e;
				}
				else {
					exceptionDuringAfterAll.addSuppressed(e);
				}
			}
		}

		if (exceptionDuringAfterAll != null) {
			context.getTestExecutionListener().testFailed(getTestDescriptor(), exceptionDuringAfterAll);
		}
	}

}

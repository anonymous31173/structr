/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import static junit.framework.TestCase.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaProperty;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.web.auth.UiAuthenticator;
import static org.structr.web.test.ResourceAccessTest.createResourceAccess;



public class SchemaAttributesInheritanceTest extends FrontendTest {

	private static final Logger logger = LoggerFactory.getLogger(SchemaAttributesInheritanceTest.class.getName());

	@Test
	public void test01InheritanceOfFileAttributesToImage() {


		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Add String property "testFile" to built-in File class
			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(33))
					.body("result[32].jsonName",       equalTo("testFile"))
					.body("result[32].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/File/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(40))
					.body("result[39].jsonName",       equalTo("testFile"))
					.body("result[39].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/Image/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	@Test
	public void test02InheritanceOfFileAttributesToSubclass() {

		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap changedProperties = new PropertyMap();
			changedProperties.put(SchemaProperty.name, "testFile");
			changedProperties.put(SchemaProperty.propertyType, "String");
			changedProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), changedProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Create new schema node for dynamic class SubFile which extends File
			SchemaNode subFile = app.create(SchemaNode.class);

			final PropertyMap subFileProperties = new PropertyMap();
			subFileProperties.put(SchemaNode.name, "SubFile");
			subFileProperties.put(SchemaNode.extendsClass, "File");
			subFile.setProperties(subFile.getSecurityContext(), subFileProperties);


			// Add String property "testSubFile" to new dynamic class
			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testSubFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, subFile);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}


		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(34))
					.body("result[32].jsonName",       equalTo("testSubFile"))
					.body("result[32].declaringClass", equalTo("SubFile"))
					.body("result[33].jsonName",       equalTo("testFile"))
					.body("result[33].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/SubFile/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}

	}

	@Test
	public void test03InheritanceOfFileAttributesToSubclassOfImage() {

		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Create new schema node for dynamic class SubFile which extends File
			SchemaNode subFile = app.create(SchemaNode.class);

			final PropertyMap subFileProperties = new PropertyMap();
			subFileProperties.put(SchemaNode.name, "SubFile");
			subFileProperties.put(SchemaNode.extendsClass, "Image");
			subFile.setProperties(subFile.getSecurityContext(), subFileProperties);


			// Add String property "testSubFile" to new dynamic class
			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testSubFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, subFile);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}


		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(41))
					.body("result[39].jsonName",       equalTo("testSubFile"))
					.body("result[39].declaringClass", equalTo("SubFile"))
					.body("result[40].jsonName",       equalTo("testFile"))
					.body("result[40].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/SubFile/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}

	}
}

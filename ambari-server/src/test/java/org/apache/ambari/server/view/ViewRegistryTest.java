/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ambari.server.view;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.bind.JAXBException;

import org.apache.ambari.server.api.resources.SubResourceDefinition;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.ResourceProvider;
import org.apache.ambari.server.orm.dao.MemberDAO;
import org.apache.ambari.server.orm.dao.PrivilegeDAO;
import org.apache.ambari.server.orm.dao.ResourceDAO;
import org.apache.ambari.server.orm.dao.ResourceTypeDAO;
import org.apache.ambari.server.orm.dao.UserDAO;
import org.apache.ambari.server.orm.dao.ViewDAO;
import org.apache.ambari.server.orm.dao.ViewInstanceDAO;
import org.apache.ambari.server.orm.entities.PermissionEntity;
import org.apache.ambari.server.orm.entities.PrivilegeEntity;
import org.apache.ambari.server.orm.entities.ResourceEntity;
import org.apache.ambari.server.orm.entities.ResourceTypeEntity;
import org.apache.ambari.server.orm.entities.ViewEntity;
import org.apache.ambari.server.orm.entities.ViewEntityTest;
import org.apache.ambari.server.orm.entities.ViewInstanceDataEntity;
import org.apache.ambari.server.orm.entities.ViewInstanceEntity;
import org.apache.ambari.server.orm.entities.ViewInstanceEntityTest;
import org.apache.ambari.server.security.SecurityHelper;
import org.apache.ambari.server.security.authorization.AmbariGrantedAuthority;
import org.apache.ambari.server.view.configuration.InstanceConfig;
import org.apache.ambari.server.view.configuration.InstanceConfigTest;
import org.apache.ambari.server.view.configuration.PropertyConfig;
import org.apache.ambari.server.view.configuration.ResourceConfig;
import org.apache.ambari.server.view.configuration.ResourceConfigTest;
import org.apache.ambari.server.view.configuration.ViewConfig;
import org.apache.ambari.server.view.configuration.ViewConfigTest;
import org.apache.ambari.server.view.events.EventImpl;
import org.apache.ambari.server.view.events.EventImplTest;
import org.apache.ambari.view.events.Event;
import org.apache.ambari.view.events.Listener;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * ViewRegistry tests.
 */
public class ViewRegistryTest {

  private static String view_xml1 = "<view>\n" +
      "    <name>MY_VIEW</name>\n" +
      "    <label>My View!</label>\n" +
      "    <version>1.0.0</version>\n" +
      "</view>";

  private static String view_xml2 = "<view>\n" +
      "    <name>MY_VIEW</name>\n" +
      "    <label>My View!</label>\n" +
      "    <version>2.0.0</version>\n" +
      "</view>";

  private static String xml_valid_instance = "<view>\n" +
      "    <name>MY_VIEW</name>\n" +
      "    <label>My View!</label>\n" +
      "    <version>1.0.0</version>\n" +
      "    <parameter>\n" +
      "        <name>p1</name>\n" +
      "        <description>Parameter 1.</description>\n" +
      "        <required>true</required>\n" +
      "    </parameter>\n" +
      "    <parameter>\n" +
      "        <name>p2</name>\n" +
      "        <description>Parameter 2.</description>\n" +
      "        <required>false</required>\n" +
      "    </parameter>\n" +
      "    <instance>\n" +
      "        <name>INSTANCE1</name>\n" +
      "        <label>My Instance 1!</label>\n" +
      "        <property>\n" +
      "            <key>p1</key>\n" +
      "            <value>v1-1</value>\n" +
      "        </property>\n" +
      "        <property>\n" +
      "            <key>p2</key>\n" +
      "            <value>v2-1</value>\n" +
      "        </property>\n" +
      "    </instance>\n" +
      "</view>";

  private static String xml_invalid_instance = "<view>\n" +
      "    <name>MY_VIEW</name>\n" +
      "    <label>My View!</label>\n" +
      "    <version>1.0.0</version>\n" +
      "    <parameter>\n" +
      "        <name>p1</name>\n" +
      "        <description>Parameter 1.</description>\n" +
      "        <required>true</required>\n" +
      "    </parameter>\n" +
      "    <parameter>\n" +
      "        <name>p2</name>\n" +
      "        <description>Parameter 2.</description>\n" +
      "        <required>false</required>\n" +
      "    </parameter>\n" +
      "    <instance>\n" +
      "        <name>INSTANCE1</name>\n" +
      "        <label>My Instance 1!</label>\n" +
      "    </instance>\n" +
      "</view>";

  // registry mocks
  private static final ViewDAO viewDAO = createMock(ViewDAO.class);
  private static final ViewInstanceDAO viewInstanceDAO = createNiceMock(ViewInstanceDAO.class);
  private static final UserDAO userDAO = createNiceMock(UserDAO.class);
  private static final MemberDAO memberDAO = createNiceMock(MemberDAO.class);
  private static final PrivilegeDAO privilegeDAO = createNiceMock(PrivilegeDAO.class);
  private static final ResourceDAO resourceDAO = createNiceMock(ResourceDAO.class);
  private static final ResourceTypeDAO resourceTypeDAO = createNiceMock(ResourceTypeDAO.class);
  private static final SecurityHelper securityHelper = createNiceMock(SecurityHelper.class);
  private static final Configuration configuration = createNiceMock(Configuration.class);
  private static final ViewInstanceHandlerList handlerList = createNiceMock(ViewInstanceHandlerList.class);

  @Test
  public void testReadViewArchives() throws Exception {
    ViewRegistry registry = getRegistry();

    Configuration configuration = createNiceMock(Configuration.class);
    File viewDir = createNiceMock(File.class);
    File extractedArchiveDir = createNiceMock(File.class);
    File viewArchive = createNiceMock(File.class);
    File archiveDir = createNiceMock(File.class);
    File entryFile  = createNiceMock(File.class);
    File classesDir = createNiceMock(File.class);
    File libDir = createNiceMock(File.class);
    File fileEntry = createNiceMock(File.class);

    JarFile viewJarFile = createNiceMock(JarFile.class);
    Enumeration<JarEntry> enumeration = createMock(Enumeration.class);
    JarEntry jarEntry = createNiceMock(JarEntry.class);
    InputStream is = createMock(InputStream.class);
    FileOutputStream fos = createMock(FileOutputStream.class);

    ResourceTypeEntity resourceTypeEntity = new ResourceTypeEntity();
    resourceTypeEntity.setId(10);
    resourceTypeEntity.setName("MY_VIEW{1.0.0}");

    ViewEntity viewDefinition = ViewEntityTest.getViewEntity();
    viewDefinition.setResourceType(resourceTypeEntity);

    Set<ViewInstanceEntity> viewInstanceEntities = ViewInstanceEntityTest.getViewInstanceEntities(viewDefinition);
    viewDefinition.setInstances(viewInstanceEntities);

    Map<File, ViewConfig> viewConfigs =
        Collections.singletonMap(viewArchive, viewDefinition.getConfiguration());

    long resourceId = 99L;
    for (ViewInstanceEntity viewInstanceEntity : viewInstanceEntities) {
      ResourceEntity resourceEntity = new ResourceEntity();
      resourceEntity.setId(resourceId);
      resourceEntity.setResourceType(resourceTypeEntity);
      viewInstanceEntity.setResource(resourceEntity);
    }

    Map<String, File> files = new HashMap<String, File>();

    files.put("/var/lib/ambari-server/resources/views/work", extractedArchiveDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}", archiveDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/view.xml", entryFile);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/WEB-INF/classes", classesDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/WEB-INF/lib", libDir);

    Map<File, FileOutputStream> outputStreams = new HashMap<File, FileOutputStream>();
    outputStreams.put(entryFile, fos);

    Map<File, JarFile> jarFiles = new HashMap<File, JarFile>();
    jarFiles.put(viewArchive, viewJarFile);

    // set expectations
    expect(configuration.getViewsDir()).andReturn(viewDir);
    expect(viewDir.getAbsolutePath()).andReturn("/var/lib/ambari-server/resources/views");

    expect(viewDir.listFiles()).andReturn(new File[]{viewArchive});

    expect(viewArchive.isDirectory()).andReturn(false);
    expect(viewArchive.getAbsolutePath()).andReturn("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}").anyTimes();

    expect(archiveDir.exists()).andReturn(false);
    expect(archiveDir.getAbsolutePath()).andReturn(
        "/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}").anyTimes();
    expect(archiveDir.mkdir()).andReturn(true);
    expect(archiveDir.toURI()).andReturn(new URI("file:./"));

    expect(viewJarFile.entries()).andReturn(enumeration);
    expect(viewJarFile.getInputStream(jarEntry)).andReturn(is);

    expect(enumeration.hasMoreElements()).andReturn(true);
    expect(enumeration.hasMoreElements()).andReturn(false);
    expect(enumeration.nextElement()).andReturn(jarEntry);

    expect(jarEntry.getName()).andReturn("view.xml");
    expect(jarEntry.isDirectory()).andReturn(false);

    expect(is.available()).andReturn(1);
    expect(is.available()).andReturn(0);

    expect(is.read()).andReturn(10);
    fos.write(10);

    fos.close();
    is.close();

    expect(extractedArchiveDir.exists()).andReturn(false);
    expect(extractedArchiveDir.mkdir()).andReturn(true);

    expect(classesDir.exists()).andReturn(true);
    expect(classesDir.toURI()).andReturn(new URI("file:./"));

    expect(libDir.exists()).andReturn(true);

    expect(libDir.listFiles()).andReturn(new File[]{fileEntry});
    expect(fileEntry.toURI()).andReturn(new URI("file:./"));

    expect(viewDAO.findByName("MY_VIEW{1.0.0}")).andReturn(viewDefinition);

    expect(viewDAO.findAll()).andReturn(Collections.<ViewEntity>emptyList());

    // replay mocks
    replay(configuration, viewDir, extractedArchiveDir, viewArchive, archiveDir, entryFile, classesDir,
        libDir, fileEntry, viewJarFile, enumeration, jarEntry, is, fos, resourceDAO, viewDAO, viewInstanceDAO);

    registry.setHelper(new TestViewRegistryHelper(viewConfigs, files, outputStreams, jarFiles));

    Set<ViewInstanceEntity> instanceEntities = registry.readViewArchives(configuration);

    Assert.assertEquals(2, instanceEntities.size());

    // verify mocks
    verify(configuration, viewDir, extractedArchiveDir, viewArchive, archiveDir, entryFile, classesDir,
        libDir, fileEntry, viewJarFile, enumeration, jarEntry, is, fos, resourceDAO, viewDAO, viewInstanceDAO);
  }

  @Test
  public void testReadViewArchives_exception() throws Exception {
    ViewRegistry registry = getRegistry();

    Configuration configuration = createNiceMock(Configuration.class);
    File viewDir = createNiceMock(File.class);
    File extractedArchiveDir = createNiceMock(File.class);
    File viewArchive = createNiceMock(File.class);
    File archiveDir = createNiceMock(File.class);
    File entryFile  = createNiceMock(File.class);
    File classesDir = createNiceMock(File.class);
    File libDir = createNiceMock(File.class);
    File fileEntry = createNiceMock(File.class);

    JarFile viewJarFile = createNiceMock(JarFile.class);
    Enumeration<JarEntry> enumeration = createMock(Enumeration.class);
    JarEntry jarEntry = createNiceMock(JarEntry.class);
    InputStream is = createMock(InputStream.class);
    FileOutputStream fos = createMock(FileOutputStream.class);

    ResourceTypeEntity resourceTypeEntity = new ResourceTypeEntity();
    resourceTypeEntity.setId(10);
    resourceTypeEntity.setName("MY_VIEW{1.0.0}");

    ViewEntity viewDefinition = ViewEntityTest.getViewEntity();
    viewDefinition.setResourceType(resourceTypeEntity);

    Set<ViewInstanceEntity> viewInstanceEntities = ViewInstanceEntityTest.getViewInstanceEntities(viewDefinition);
    viewDefinition.setInstances(viewInstanceEntities);

    Map<File, ViewConfig> viewConfigs =
        Collections.singletonMap(viewArchive, viewDefinition.getConfiguration());

    long resourceId = 99L;
    for (ViewInstanceEntity viewInstanceEntity : viewInstanceEntities) {
      ResourceEntity resourceEntity = new ResourceEntity();
      resourceEntity.setId(resourceId);
      resourceEntity.setResourceType(resourceTypeEntity);
      viewInstanceEntity.setResource(resourceEntity);
    }

    Map<String, File> files = new HashMap<String, File>();

    files.put("/var/lib/ambari-server/resources/views/work", extractedArchiveDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}", archiveDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/view.xml", entryFile);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/WEB-INF/classes", classesDir);
    files.put("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}/WEB-INF/lib", libDir);

    Map<File, FileOutputStream> outputStreams = new HashMap<File, FileOutputStream>();
    outputStreams.put(entryFile, fos);

    Map<File, JarFile> jarFiles = new HashMap<File, JarFile>();
    jarFiles.put(viewArchive, viewJarFile);

    // set expectations
    expect(configuration.getViewsDir()).andReturn(viewDir);
    expect(viewDir.getAbsolutePath()).andReturn("/var/lib/ambari-server/resources/views");

    expect(viewDir.listFiles()).andReturn(new File[]{viewArchive});

    expect(viewArchive.isDirectory()).andReturn(false);
    expect(viewArchive.getAbsolutePath()).andReturn("/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}");

    expect(archiveDir.exists()).andReturn(false);
    expect(archiveDir.getAbsolutePath()).andReturn(
        "/var/lib/ambari-server/resources/views/work/MY_VIEW{1.0.0}").anyTimes();
    expect(archiveDir.mkdir()).andReturn(true);
    expect(archiveDir.toURI()).andReturn(new URI("file:./"));

    expect(viewJarFile.entries()).andReturn(enumeration);
    expect(viewJarFile.getInputStream(jarEntry)).andReturn(is);

    expect(enumeration.hasMoreElements()).andReturn(true);
    expect(enumeration.hasMoreElements()).andReturn(false);
    expect(enumeration.nextElement()).andReturn(jarEntry);

    expect(jarEntry.getName()).andReturn("view.xml");
    expect(jarEntry.isDirectory()).andReturn(false);

    expect(is.available()).andReturn(1);
    expect(is.available()).andReturn(0);

    expect(is.read()).andReturn(10);
    fos.write(10);

    fos.close();
    is.close();

    expect(extractedArchiveDir.exists()).andReturn(false);
    expect(extractedArchiveDir.mkdir()).andReturn(true);

    expect(classesDir.exists()).andReturn(true);
    expect(classesDir.toURI()).andReturn(new URI("file:./"));

    expect(libDir.exists()).andReturn(true);

    expect(libDir.listFiles()).andReturn(new File[]{fileEntry});
    expect(fileEntry.toURI()).andReturn(new URI("file:./"));

    expect(viewDAO.findAll()).andReturn(Collections.<ViewEntity>emptyList());
    expect(viewDAO.findByName("MY_VIEW{1.0.0}")).andThrow(new IllegalArgumentException("Expected exception."));

    // replay mocks
    replay(configuration, viewDir, extractedArchiveDir, viewArchive, archiveDir, entryFile, classesDir,
        libDir, fileEntry, viewJarFile, enumeration, jarEntry, is, fos, viewDAO);

    registry.setHelper(new TestViewRegistryHelper(viewConfigs, files, outputStreams, jarFiles));

    Set<ViewInstanceEntity> instanceEntities = registry.readViewArchives(configuration);

    Assert.assertEquals(0, instanceEntities.size());

    // verify mocks
    verify(configuration, viewDir, extractedArchiveDir, viewArchive, archiveDir, entryFile, classesDir,
        libDir, fileEntry, viewJarFile, enumeration, jarEntry, is, fos, viewDAO);
  }

  @Test
  public void testListener() throws Exception {
    ViewRegistry registry = getRegistry();

    TestListener listener = new TestListener();
    registry.registerListener(listener, "MY_VIEW", "1.0.0");

    EventImpl event = EventImplTest.getEvent("MyEvent", Collections.<String, String>emptyMap(), view_xml1);

    registry.fireEvent(event);

    Assert.assertEquals(event, listener.getLastEvent());

    listener.clear();

    event = EventImplTest.getEvent("MyEvent", Collections.<String, String>emptyMap(), view_xml2);

    registry.fireEvent(event);

    Assert.assertNull(listener.getLastEvent());
  }

  @Test
  public void testAddGetDefinitions() throws Exception {
    ViewEntity viewDefinition = ViewEntityTest.getViewEntity();

    ViewRegistry registry = getRegistry();

    registry.addDefinition(viewDefinition);

    Assert.assertEquals(viewDefinition, registry.getDefinition("MY_VIEW", "1.0.0"));

    Collection<ViewEntity> viewDefinitions = registry.getDefinitions();

    Assert.assertEquals(1, viewDefinitions.size());

    Assert.assertEquals(viewDefinition, viewDefinitions.iterator().next());
  }

  @Test
  public void testAddGetInstanceDefinitions() throws Exception {
    ViewEntity viewDefinition = ViewEntityTest.getViewEntity();
    ViewInstanceEntity viewInstanceDefinition = ViewInstanceEntityTest.getViewInstanceEntity();

    ViewRegistry registry = getRegistry();

    registry.addDefinition(viewDefinition);

    registry.addInstanceDefinition(viewDefinition, viewInstanceDefinition);

    Assert.assertEquals(viewInstanceDefinition, registry.getInstanceDefinition("MY_VIEW", "1.0.0", "INSTANCE1"));

    Collection<ViewInstanceEntity> viewInstanceDefinitions = registry.getInstanceDefinitions(viewDefinition);

    Assert.assertEquals(1, viewInstanceDefinitions.size());

    Assert.assertEquals(viewInstanceDefinition, viewInstanceDefinitions.iterator().next());
  }

  @Test
  public void testGetSubResourceDefinitions() throws Exception {
    ViewEntity viewDefinition = ViewEntityTest.getViewEntity();
    ViewRegistry registry = getRegistry();

    ResourceConfig config = ResourceConfigTest.getResourceConfigs().get(0);
    Resource.Type type1 = new Resource.Type("myType");

    ResourceProvider provider1 = createNiceMock(ResourceProvider.class);
    viewDefinition.addResourceProvider(type1, provider1);

    viewDefinition.addResourceConfiguration(type1, config);
    registry.addDefinition(viewDefinition);
    Set<SubResourceDefinition> subResourceDefinitions = registry.getSubResourceDefinitions("MY_VIEW", "1.0.0");

    Assert.assertEquals(1, subResourceDefinitions.size());
    Assert.assertEquals("myType", subResourceDefinitions.iterator().next().getType().name());
  }

  @Test
  public void testAddInstanceDefinition() throws Exception {
    ViewRegistry registry = getRegistry();

    ViewEntity viewEntity = ViewEntityTest.getViewEntity();
    InstanceConfig instanceConfig = InstanceConfigTest.getInstanceConfigs().get(0);

    ViewInstanceEntity viewInstanceEntity = new ViewInstanceEntity(viewEntity, instanceConfig);

    ResourceTypeEntity resourceTypeEntity = new ResourceTypeEntity();
    resourceTypeEntity.setId(10);
    resourceTypeEntity.setName(viewEntity.getName());

    viewEntity.setResourceType(resourceTypeEntity);

    ResourceEntity resourceEntity = new ResourceEntity();
    resourceEntity.setId(20L);
    resourceEntity.setResourceType(resourceTypeEntity);
    viewInstanceEntity.setResource(resourceEntity);

    registry.addDefinition(viewEntity);
    registry.addInstanceDefinition(viewEntity, viewInstanceEntity);

    Collection<ViewInstanceEntity> viewInstanceDefinitions = registry.getInstanceDefinitions(viewEntity);

    Assert.assertEquals(1, viewInstanceDefinitions.size());

    Assert.assertEquals(viewInstanceEntity, viewInstanceDefinitions.iterator().next());
  }

  @Test
  public void testInstallViewInstance() throws Exception {

    ViewRegistry registry = getRegistry();

    Properties properties = new Properties();
    properties.put("p1", "v1");

    Configuration ambariConfig = new Configuration(properties);

    ViewConfig config = ViewConfigTest.getConfig(xml_valid_instance);
    ViewEntity viewEntity = getViewEntity(config, ambariConfig, getClass().getClassLoader(), "");
    ViewInstanceEntity viewInstanceEntity = getViewInstanceEntity(viewEntity, config.getInstances().get(0));

    expect(viewInstanceDAO.merge(viewInstanceEntity)).andReturn(null);
    expect(viewInstanceDAO.findByName("MY_VIEW{1.0.0}", viewInstanceEntity.getInstanceName())).andReturn(viewInstanceEntity);

    handlerList.addViewInstance(viewInstanceEntity);

    replay(viewDAO, viewInstanceDAO, securityHelper, handlerList);

    registry.addDefinition(viewEntity);
    registry.installViewInstance(viewInstanceEntity);

    Collection<ViewInstanceEntity> viewInstanceDefinitions = registry.getInstanceDefinitions(viewEntity);

    Assert.assertEquals(1, viewInstanceDefinitions.size());

    Assert.assertEquals(viewInstanceEntity, viewInstanceDefinitions.iterator().next());

    verify(viewDAO, viewInstanceDAO, securityHelper, handlerList);
  }

  @Test
  public void testInstallViewInstance_invalid() throws Exception {

    ViewRegistry registry = getRegistry();

    Properties properties = new Properties();
    properties.put("p1", "v1");

    Configuration ambariConfig = new Configuration(properties);

    ViewConfig config = ViewConfigTest.getConfig(xml_invalid_instance);
    ViewEntity viewEntity = getViewEntity(config, ambariConfig, getClass().getClassLoader(), "");
    ViewInstanceEntity viewInstanceEntity = getViewInstanceEntity(viewEntity, config.getInstances().get(0));

    replay(viewDAO, viewInstanceDAO, securityHelper, resourceTypeDAO);

    registry.addDefinition(viewEntity);
    try {
      registry.installViewInstance(viewInstanceEntity);
      Assert.fail("expected an IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    verify(viewDAO, viewInstanceDAO, securityHelper, resourceTypeDAO);
  }

  @Test
  public void testInstallViewInstance_unknownView() throws Exception {

    ViewRegistry registry = getRegistry();

    Properties properties = new Properties();
    properties.put("p1", "v1");

    Configuration ambariConfig = new Configuration(properties);

    ViewConfig config = ViewConfigTest.getConfig(xml_valid_instance);
    ViewEntity viewEntity = getViewEntity(config, ambariConfig, getClass().getClassLoader(), "");
    ViewInstanceEntity viewInstanceEntity = getViewInstanceEntity(viewEntity, config.getInstances().get(0));
    viewInstanceEntity.setViewName("BOGUS_VIEW");

    replay(viewDAO, viewInstanceDAO, securityHelper, resourceTypeDAO);

    registry.addDefinition(viewEntity);
    try {
      registry.installViewInstance(viewInstanceEntity);
      Assert.fail("expected an IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // expected
    }
    verify(viewDAO, viewInstanceDAO, securityHelper, resourceTypeDAO);
  }

  @Test
  public void testUpdateViewInstance() throws Exception {

    ViewRegistry registry = getRegistry();

    Properties properties = new Properties();
    properties.put("p1", "v1");

    Configuration ambariConfig = new Configuration(properties);

    ViewConfig config = ViewConfigTest.getConfig(xml_valid_instance);
    ViewEntity viewEntity = getViewEntity(config, ambariConfig, getClass().getClassLoader(), "");
    ViewInstanceEntity viewInstanceEntity = getViewInstanceEntity(viewEntity, config.getInstances().get(0));
    ViewInstanceEntity updateInstance = getViewInstanceEntity(viewEntity, config.getInstances().get(0));

    expect(viewInstanceDAO.merge(viewInstanceEntity)).andReturn(viewInstanceEntity);
    expect(viewInstanceDAO.findByName("MY_VIEW{1.0.0}", viewInstanceEntity.getInstanceName())).andReturn(viewInstanceEntity);

    replay(viewDAO, viewInstanceDAO, securityHelper);

    registry.addDefinition(viewEntity);
    registry.installViewInstance(viewInstanceEntity);

    registry.updateViewInstance(updateInstance);

    Collection<ViewInstanceEntity> viewInstanceDefinitions = registry.getInstanceDefinitions(viewEntity);

    Assert.assertEquals(1, viewInstanceDefinitions.size());

    Assert.assertEquals(viewInstanceEntity, viewInstanceDefinitions.iterator().next());

    verify(viewDAO, viewInstanceDAO, securityHelper);
  }

  @Test
  public void testUpdateViewInstance_invalid() throws Exception {

    ViewRegistry registry = getRegistry();

    Properties properties = new Properties();
    properties.put("p1", "v1");

    Configuration ambariConfig = new Configuration(properties);

    ViewConfig config = ViewConfigTest.getConfig(xml_valid_instance);
    ViewConfig invalidConfig = ViewConfigTest.getConfig(xml_invalid_instance);
    ViewEntity viewEntity = getViewEntity(config, ambariConfig, getClass().getClassLoader(), "");
    ViewInstanceEntity viewInstanceEntity = getViewInstanceEntity(viewEntity, config.getInstances().get(0));
    ViewInstanceEntity updateInstance = getViewInstanceEntity(viewEntity, invalidConfig.getInstances().get(0));

    expect(viewInstanceDAO.merge(viewInstanceEntity)).andReturn(null);
    expect(viewInstanceDAO.findByName("MY_VIEW{1.0.0}", viewInstanceEntity.getInstanceName())).andReturn(viewInstanceEntity);

    replay(viewDAO, viewInstanceDAO, securityHelper);

    registry.addDefinition(viewEntity);
    registry.installViewInstance(viewInstanceEntity);

    try {
      registry.updateViewInstance(updateInstance);
      Assert.fail("expected an IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
    verify(viewDAO, viewInstanceDAO, securityHelper);
  }

  @Test
  public void testRemoveInstanceData() throws Exception {

    ViewRegistry registry = getRegistry();

    ViewInstanceEntity viewInstanceEntity = ViewInstanceEntityTest.getViewInstanceEntity();

    viewInstanceEntity.putInstanceData("foo", "value");

    ViewInstanceDataEntity dataEntity = viewInstanceEntity.getInstanceData("foo");

    viewInstanceDAO.removeData(dataEntity);
    expect(viewInstanceDAO.merge(viewInstanceEntity)).andReturn(viewInstanceEntity);
    replay(viewDAO, viewInstanceDAO, securityHelper);

    registry.removeInstanceData(viewInstanceEntity, "foo");

    Assert.assertNull(viewInstanceEntity.getInstanceData("foo"));
    verify(viewDAO, viewInstanceDAO, securityHelper);
  }

  @Test
  public void testIncludeDefinitionForAdmin() {
    ViewRegistry viewRegistry = getRegistry();
    ViewEntity viewEntity = createNiceMock(ViewEntity.class);
    AmbariGrantedAuthority adminAuthority = createNiceMock(AmbariGrantedAuthority.class);
    PrivilegeEntity privilegeEntity = createNiceMock(PrivilegeEntity.class);
    PermissionEntity permissionEntity = createNiceMock(PermissionEntity.class);

    Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
    authorities.add(adminAuthority);

    securityHelper.getCurrentAuthorities();
    EasyMock.expectLastCall().andReturn(authorities);
    expect(adminAuthority.getPrivilegeEntity()).andReturn(privilegeEntity);
    expect(privilegeEntity.getPermission()).andReturn(permissionEntity);
    expect(permissionEntity.getId()).andReturn(PermissionEntity.AMBARI_ADMIN_PERMISSION);

    expect(configuration.getApiAuthentication()).andReturn(true);
    replay(securityHelper, adminAuthority, privilegeEntity, permissionEntity, configuration);

    Assert.assertTrue(viewRegistry.includeDefinition(viewEntity));

    verify(securityHelper, adminAuthority, privilegeEntity, permissionEntity, configuration);
  }

  @Test
  public void testIncludeDefinitionForUserNoInstances() {
    ViewRegistry viewRegistry = getRegistry();
    ViewEntity viewEntity = createNiceMock(ViewEntity.class);

    Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();

    Collection<ViewInstanceEntity> instances = new ArrayList<ViewInstanceEntity>();

    securityHelper.getCurrentAuthorities();
    EasyMock.expectLastCall().andReturn(authorities);
    expect(viewEntity.getInstances()).andReturn(instances);

    expect(configuration.getApiAuthentication()).andReturn(true);
    replay(securityHelper, viewEntity, configuration);

    Assert.assertFalse(viewRegistry.includeDefinition(viewEntity));

    verify(securityHelper, viewEntity, configuration);
  }

  @Test
  public void testIncludeDefinitionForUserHasAccess() {
    ViewRegistry viewRegistry = getRegistry();
    ViewEntity viewEntity = createNiceMock(ViewEntity.class);
    ViewInstanceEntity instanceEntity = createNiceMock(ViewInstanceEntity.class);
    ResourceEntity resourceEntity = createNiceMock(ResourceEntity.class);
    AmbariGrantedAuthority viewUseAuthority = createNiceMock(AmbariGrantedAuthority.class);
    PrivilegeEntity privilegeEntity = createNiceMock(PrivilegeEntity.class);
    PermissionEntity permissionEntity = createNiceMock(PermissionEntity.class);

    Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
    authorities.add(viewUseAuthority);

    Collection<ViewInstanceEntity> instances = new ArrayList<ViewInstanceEntity>();
    instances.add(instanceEntity);

    expect(viewEntity.getInstances()).andReturn(instances);
    expect(instanceEntity.getResource()).andReturn(resourceEntity);
    expect(viewUseAuthority.getPrivilegeEntity()).andReturn(privilegeEntity).anyTimes();
    expect(privilegeEntity.getPermission()).andReturn(permissionEntity).anyTimes();
    expect(privilegeEntity.getResource()).andReturn(resourceEntity).anyTimes();
    expect(permissionEntity.getId()).andReturn(PermissionEntity.VIEW_USE_PERMISSION).anyTimes();
    securityHelper.getCurrentAuthorities();
    EasyMock.expectLastCall().andReturn(authorities).anyTimes();
    expect(configuration.getApiAuthentication()).andReturn(true);
    replay(securityHelper, viewEntity, instanceEntity, viewUseAuthority, privilegeEntity, permissionEntity, configuration);

    Assert.assertTrue(viewRegistry.includeDefinition(viewEntity));

    verify(securityHelper, viewEntity, instanceEntity, viewUseAuthority, privilegeEntity, permissionEntity, configuration);
  }

  @Test
  public void testIncludeDefinitionForNoApiAuthentication() {
    ViewRegistry viewRegistry = getRegistry();
    ViewEntity viewEntity = createNiceMock(ViewEntity.class);

    expect(configuration.getApiAuthentication()).andReturn(false);
    replay(securityHelper, viewEntity, configuration);

    Assert.assertTrue(viewRegistry.includeDefinition(viewEntity));

    verify(securityHelper, viewEntity, configuration);
  }

  public class TestViewRegistryHelper extends ViewRegistry.ViewRegistryHelper {
    private final Map<File, ViewConfig> viewConfigs;
    private final Map<String, File> files;
    private final Map<File, FileOutputStream> outputStreams;
    private final Map<File, JarFile> jarFiles;

    public TestViewRegistryHelper(Map<File, ViewConfig> viewConfigs, Map<String, File> files, Map<File,
        FileOutputStream> outputStreams, Map<File, JarFile> jarFiles) {
      this.viewConfigs = viewConfigs;
      this.files = files;
      this.outputStreams = outputStreams;
      this.jarFiles = jarFiles;
    }

    @Override
    public ViewConfig getViewConfigFromArchive(File archiveFile) throws MalformedURLException, JAXBException {
      return viewConfigs.get(archiveFile);
    }

    public ViewConfig getViewConfigFromExtractedArchive(String archivePath)
        throws JAXBException, FileNotFoundException {
      for (File viewConfigKey: viewConfigs.keySet()) {
        if (viewConfigKey.getAbsolutePath().equals(archivePath)) {
          return viewConfigs.get(viewConfigKey);
        }
      }
      return null;
    }

    @Override
    public File getFile(String path) {
      return files.get(path);
    }

    @Override
    public FileOutputStream getFileOutputStream(File file) throws FileNotFoundException {
      return outputStreams.get(file);
    }

    @Override
    public JarFile getJarFile(File file) throws IOException {
      return jarFiles.get(file);
    }
  }

  private static class TestListener implements Listener {
    private Event lastEvent = null;

    @Override
    public void notify(Event event) {
      lastEvent = event;
    }

    public Event getLastEvent() {
      return lastEvent;
    }

    public void clear() {
      lastEvent = null;
    }
  }

  private static ViewRegistry getRegistry() {
    ViewRegistry instance = getRegistry(viewDAO, viewInstanceDAO,
        userDAO, memberDAO, privilegeDAO,
        resourceDAO, resourceTypeDAO, securityHelper);

    reset(viewDAO, resourceDAO, viewInstanceDAO, userDAO, memberDAO,
        privilegeDAO, resourceTypeDAO, securityHelper, configuration);

    return instance;
  }

  public static ViewRegistry getRegistry(ViewDAO viewDAO, ViewInstanceDAO viewInstanceDAO,
                                  UserDAO userDAO, MemberDAO memberDAO,
                                  PrivilegeDAO privilegeDAO, ResourceDAO resourceDAO,
                                  ResourceTypeDAO resourceTypeDAO, SecurityHelper securityHelper ) {

    ViewRegistry instance = new ViewRegistry();

    instance.viewDAO = viewDAO;
    instance.resourceDAO = resourceDAO;
    instance.instanceDAO = viewInstanceDAO;
    instance.userDAO = userDAO;
    instance.memberDAO = memberDAO;
    instance.privilegeDAO = privilegeDAO;
    instance.resourceTypeDAO = resourceTypeDAO;
    instance.securityHelper = securityHelper;
    instance.configuration = configuration;
    instance.handlerList = handlerList;

    return instance;
  }

  public static ViewEntity getViewEntity(ViewConfig viewConfig, Configuration ambariConfig,
                                     ClassLoader cl, String archivePath) throws Exception{
    ViewRegistry registry = getRegistry();

    return registry.createViewDefinition(viewConfig, ambariConfig, cl, archivePath);
  }

  public static ViewInstanceEntity getViewInstanceEntity(ViewEntity viewDefinition, InstanceConfig instanceConfig) throws Exception {
    ViewRegistry registry = getRegistry();

    ViewInstanceEntity viewInstanceDefinition =
        new ViewInstanceEntity(viewDefinition, instanceConfig);

    for (PropertyConfig propertyConfig : instanceConfig.getProperties()) {
      viewInstanceDefinition.putProperty(propertyConfig.getKey(), propertyConfig.getValue());
    }

    registry.bindViewInstance(viewDefinition, viewInstanceDefinition);
    return viewInstanceDefinition;
  }
}

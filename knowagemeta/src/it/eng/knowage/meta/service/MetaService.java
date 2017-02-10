/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.
 *
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.knowage.meta.service;

import it.eng.knowage.meta.exception.KnowageMetaException;
import it.eng.knowage.meta.generator.GenerationException;
import it.eng.knowage.meta.generator.jpamapping.JpaMappingJarGenerator;
import it.eng.knowage.meta.initializer.BusinessModelInitializer;
import it.eng.knowage.meta.initializer.OlapModelInitializer;
import it.eng.knowage.meta.initializer.PhysicalModelInitializer;
import it.eng.knowage.meta.initializer.descriptor.BusinessViewInnerJoinRelationshipDescriptor;
import it.eng.knowage.meta.initializer.descriptor.CalculatedFieldDescriptor;
import it.eng.knowage.meta.initializer.descriptor.HierarchyDescriptor;
import it.eng.knowage.meta.initializer.descriptor.HierarchyLevelDescriptor;
import it.eng.knowage.meta.initializer.properties.OlapModelPropertiesFromFileInitializer;
import it.eng.knowage.meta.model.Model;
import it.eng.knowage.meta.model.ModelFactory;
import it.eng.knowage.meta.model.ModelPropertyType;
import it.eng.knowage.meta.model.business.BusinessColumn;
import it.eng.knowage.meta.model.business.BusinessColumnSet;
import it.eng.knowage.meta.model.business.BusinessModel;
import it.eng.knowage.meta.model.business.BusinessModelFactory;
import it.eng.knowage.meta.model.business.BusinessRelationship;
import it.eng.knowage.meta.model.business.BusinessTable;
import it.eng.knowage.meta.model.business.BusinessView;
import it.eng.knowage.meta.model.business.BusinessViewInnerJoinRelationship;
import it.eng.knowage.meta.model.business.CalculatedBusinessColumn;
import it.eng.knowage.meta.model.business.SimpleBusinessColumn;
import it.eng.knowage.meta.model.business.impl.BusinessRelationshipImpl;
import it.eng.knowage.meta.model.business.impl.SimpleBusinessColumnImpl;
import it.eng.knowage.meta.model.filter.PhysicalTableFilter;
import it.eng.knowage.meta.model.olap.Dimension;
import it.eng.knowage.meta.model.olap.Hierarchy;
import it.eng.knowage.meta.model.olap.Level;
import it.eng.knowage.meta.model.olap.OlapModel;
import it.eng.knowage.meta.model.physical.PhysicalColumn;
import it.eng.knowage.meta.model.physical.PhysicalForeignKey;
import it.eng.knowage.meta.model.physical.PhysicalModel;
import it.eng.knowage.meta.model.physical.PhysicalTable;
import it.eng.knowage.meta.model.serializer.EmfXmiSerializer;
import it.eng.knowage.meta.model.serializer.ModelPropertyFactory;
import it.eng.spago.security.IEngUserProfile;
import it.eng.spagobi.commons.bo.UserProfile;
import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.commons.utilities.SpagoBIUtilities;
import it.eng.spagobi.services.rest.annotations.ManageAuthorization;
import it.eng.spagobi.services.serialization.JsonConverter;
import it.eng.spagobi.tenant.Tenant;
import it.eng.spagobi.tenant.TenantManager;
import it.eng.spagobi.tools.catalogue.bo.Content;
import it.eng.spagobi.tools.catalogue.bo.MetaModel;
import it.eng.spagobi.tools.catalogue.dao.IMetaModelsDAO;
import it.eng.spagobi.tools.datasource.bo.DataSource;
import it.eng.spagobi.utilities.JSError;
import it.eng.spagobi.utilities.assertion.Assert;
import it.eng.spagobi.utilities.exceptions.SpagoBIException;
import it.eng.spagobi.utilities.exceptions.SpagoBIServiceException;
import it.eng.spagobi.utilities.rest.RestUtilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.Pointer;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.safehaus.uuid.UUID;
import org.safehaus.uuid.UUIDGenerator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.flipkart.zjsonpatch.JsonDiff;

@ManageAuthorization
@Path("/1.0/metaWeb")
public class MetaService extends AbstractSpagoBIResource {
	private static Logger logger = Logger.getLogger(MetaService.class);
	private static final String DEFAULT_MODEL_NAME = "modelName";
	public static final String EMF_MODEL = "EMF_MODEL";

	/**
	 * Gets a json like this {datasourceId: 'xxx', physicalModels: ['name1', 'name2', ...], businessModels: ['name1', 'name2', ...]}
	 *
	 * @param dsId
	 * @return
	 */

	@POST
	@Path("/create")
	public Response createModels(@Context HttpServletRequest req) {
		try {
			JSONObject json = RestUtilities.readBodyAsJSONObject(req);

			IEngUserProfile profile = (IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE);
			if (profile != null) {
				UserProfile userProfile = (UserProfile) profile;
				TenantManager.setTenant(new Tenant(userProfile.getOrganization()));
			}

			Model model = createEmptyModel(json);
			req.getSession().setAttribute(EMF_MODEL, model);

			JSONObject translatedModel = createJson(model);

			return Response.ok(translatedModel.toString()).build();

		} catch (IOException | JSONException e) {
			logger.error(e);
		}
		return Response.serverError().build();
	}

	@GET
	@Path("/loadSbiModel/{bmId}")
	public Response loadSbiModel(@PathParam("bmId") Integer bmId, @Context HttpServletRequest req) {
		try {
			IMetaModelsDAO businessModelsDAO = DAOFactory.getMetaModelsDAO();
			businessModelsDAO.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));

			EmfXmiSerializer serializer = new EmfXmiSerializer();
			Content lastFileModelContent = businessModelsDAO.lastFileModelMeta(bmId);
			InputStream is = new ByteArrayInputStream(lastFileModelContent.getFileModel());
			Model model = serializer.deserialize(is);
			req.getSession().setAttribute(EMF_MODEL, model);

			JSONObject translatedModel = createJson(model);

			return Response.ok(translatedModel.toString()).build();

		} catch (Throwable t) {
			throw new SpagoBIServiceException(req.getPathInfo(), t);
		}
	}

	@POST
	@Path("/generateModel")
	public Response generateModel(@Context HttpServletRequest req) {
		try {
			EmfXmiSerializer serializer = new EmfXmiSerializer();
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);

			Assert.assertTrue(jsonRoot.has("data"), "data model is mandatory");
			JSONObject jsonData = jsonRoot.getJSONObject("data");
			String modelName = jsonData.getString("name");
			Integer modelId = jsonData.getInt("id");

			applyDiff(jsonRoot, model);

			ByteArrayOutputStream filee = new ByteArrayOutputStream();
			serializer.serialize(model, filee);

			IMetaModelsDAO businessModelsDAO = DAOFactory.getMetaModelsDAO();
			businessModelsDAO.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));
			Content content = new Content();
			byte[] bytes = filee.toByteArray();
			content.setFileName(modelName + ".sbimodel");
			content.setFileModel(bytes);
			content.setCreationDate(new Date());
			UserProfile profile = (UserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE);
			TenantManager.setTenant(new Tenant(profile.getOrganization()));
			content.setCreationUser(profile.getUserId().toString());
			Content metaModelContent = businessModelsDAO.loadActiveMetaModelContentByName(modelName);
			if (metaModelContent == null || metaModelContent.getFileModel() == null
					|| (metaModelContent.getFileModel() != null && metaModelContent.getContent() != null)) {
				businessModelsDAO.insertMetaModelContent(modelId, content);
			} else {
				businessModelsDAO.modifyMetaModelContent(modelId, content, metaModelContent.getId());
			}

			return Response.ok().build();
		} catch (IOException | JSONException e) {
			logger.error(e);
		} catch (SpagoBIException e) {
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
		return Response.serverError().build();
	}

	@POST
	@Path("/addBusinessClass")
	public Response addBusinessClass(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);

			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
			JSONObject oldJsonModel = createJson(model);

			applyDiff(jsonRoot, model);

			JSONObject json = jsonRoot.getJSONObject("data");
			ObjectMapper mapper = new ObjectMapper();
			JsonNode newBM = mapper.readTree(json.toString());
			applyBusinessClass(newBM, model);

			JSONObject jsonModel = createJson(model);
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
		} catch (SpagoBIException e) {
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
		return Response.serverError().build();
	}

	@POST
	@Path("/addBusinessRelation")
	public Response addBusinessRelation(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);

			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);

			JSONObject oldJsonModel = createJson(model);

			applyDiff(jsonRoot, model);

			JSONObject json = jsonRoot.getJSONObject("data");

			// Setting uniqueName equals to name because front-end sends only a "name" field
			String name = json.getString("name");
			json.put("uniqueName", name);

			BusinessModel bm = model.getBusinessModels().get(0);

			ObjectMapper mapper = new ObjectMapper();
			JsonNode rel = mapper.readTree(json.toString());
			applyRelationships(rel, bm);

			JSONObject jsonModel = createJson(model);
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
		} catch (SpagoBIException e) {
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
		return Response.serverError().build();
	}

	/*
	 * Just apply the patch without adding new data
	 */
	@POST
	@Path("/updateModel")
	public Response updateModel(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
			JSONObject oldJsonModel = createJson(model);
			applyDiff(jsonRoot, model);
			JSONObject jsonModel = createJson(model);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
		} catch (SpagoBIException e) {
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
		return Response.serverError().build();
	}

	@POST
	@Path("/addBusinessView")
	public Response addBusinessView(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
			JSONObject oldJsonModel = createJson(model);

			applyDiff(jsonRoot, model);

			JSONObject json = jsonRoot.getJSONObject("data");

			JSONObject relationships = json.getJSONObject("relationships");

			BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();
			BusinessModel bm = model.getBusinessModels().get(0);
			PhysicalModel physicalModel = model.getPhysicalModels().get(0);

			BusinessView bw = null;
			if (json.has("viewUniqueName")) {
				bw = bm.getBusinessViewByUniqueName(json.getString("viewUniqueName"));
				// Clearing old join relationships
				bw.getJoinRelationships().clear();
			} else {
				bw = BusinessModelFactory.eINSTANCE.createBusinessView();
				bw.setName(json.getString("name"));
				bm.addBusinessView(bw);
				bw.setDescription(json.optString("description"));

				// BusinessTable sourceBusinessClass = bm.getBusinessTableByUniqueName(json.getString("sourceBusinessClass"));

				// Adding source table only if it has not been selected
				JSONArray physicaltables = json.getJSONArray("physicaltable");
				// boolean addSourceTable = true;
				for (int i = 0; i < physicaltables.length(); i++) {
					String ptName = physicaltables.getString(i);
					// if (ptName.equals(sourceBusinessClass.getPhysicalTable().getName())) {
					// addSourceTable = false;
					// }
					PhysicalTable pt = physicalModel.getTable(ptName);
					bw.getPhysicalTables().add(pt);

					EList<PhysicalColumn> ptcol = pt.getColumns();
					for (int pci = 0; pci < ptcol.size(); pci++) {
						businessModelInitializer.addColumn(ptcol.get(pci), bw);
					}
				}
				// if (addSourceTable) {
				// physicaltables.put(sourceBusinessClass.getPhysicalTable().getName());
				// }

				// Copy relationships
				// Iterator<BusinessRelationship> sourceRelIterator = sourceBusinessClass.getRelationships().iterator();
				// BusinessModelFactory.eINSTANCE.createBusinessRelationship();
				// while (sourceRelIterator.hasNext()) {
				// BusinessRelationship sourceRel = sourceRelIterator.next();
				// String relationshipName = sourceRel.getName();
				// BusinessColumnSet source = null;
				// if (sourceRel.getSourceTable().getUniqueName().equals(sourceBusinessClass.getUniqueName())) {
				// source = bw;
				// } else {
				// source = sourceRel.getSourceTable();
				// }
				// BusinessColumnSet destination = null;
				// if (sourceRel.getDestinationTable().getUniqueName().equals(sourceBusinessClass.getUniqueName())) {
				// destination = bw;
				// } else {
				// destination = sourceRel.getDestinationTable();
				// }
				// ModelPropertyType cardinalityType = sourceRel.getPropertyType(BusinessModelPropertiesFromFileInitializer.RELATIONSHIP_CARDINALITY);
				// String cardinality = sourceRel.getProperties().get(cardinalityType.getId()).getValue();
				// List<BusinessColumn> destinationCol = sourceRel.getDestinationColumns();
				// List<BusinessColumn> sourceCol = sourceRel.getSourceColumns();
				// businessModelInitializer.addRelationship(new BusinessRelationshipDescriptor(source, destination, sourceCol, destinationCol, cardinality,
				// relationshipName));
				// }
				//
				// // Add identifiers
				// BusinessIdentifier identifier = sourceBusinessClass.getIdentifier();
				// businessModelInitializer.addIdentifier(identifier.getName(), bw, identifier.getColumns());

			}
			List<BusinessViewInnerJoinRelationshipDescriptor> innerJoinRelationshipDescriptors = new ArrayList<>();

			// Calculating join relationships to add to BusinessView
			Iterator<String> relationshipsIterator = relationships.keys();
			while (relationshipsIterator.hasNext()) {
				String tableName = relationshipsIterator.next();
				PhysicalTable destinationTable = physicalModel.getTable(tableName);

				JSONObject destinationColumns = relationships.getJSONObject(tableName);
				Iterator<String> destinationColumnsIterator = destinationColumns.keys();
				while (destinationColumnsIterator.hasNext()) {
					String destinationColumnName = destinationColumnsIterator.next();
					PhysicalColumn destinationColumn = destinationTable.getColumn(destinationColumnName);

					JSONObject sourceTables = destinationColumns.getJSONObject(destinationColumnName);
					Iterator<String> sourceTablesIterator = sourceTables.keys();
					while (sourceTablesIterator.hasNext()) {
						String sourceTableName = sourceTablesIterator.next();
						PhysicalTable sourceTable = physicalModel.getTable(sourceTableName);

						BusinessViewInnerJoinRelationshipDescriptor innerJoinRelationshipDescriptor = new BusinessViewInnerJoinRelationshipDescriptor(
								sourceTable, destinationTable);
						int index = innerJoinRelationshipDescriptors.indexOf(innerJoinRelationshipDescriptor);
						if (index < 0) {
							innerJoinRelationshipDescriptors.add(innerJoinRelationshipDescriptor);
						} else {
							innerJoinRelationshipDescriptor = innerJoinRelationshipDescriptors.get(index);
						}
						innerJoinRelationshipDescriptor.getDestinationColumns().add(destinationColumn);

						JSONArray sourceColumns = sourceTables.getJSONArray(sourceTableName);
						for (int x = 0; x < sourceColumns.length(); x++) {
							String sourceColName = sourceColumns.getString(x);
							PhysicalColumn sourceCol = sourceTable.getColumn(sourceColName);

							innerJoinRelationshipDescriptor.getSourceColumns().add(sourceCol);
						}
					}
				}
			}

			// Adding join relationships to BusinessView
			for (BusinessViewInnerJoinRelationshipDescriptor businessViewInnerJoinRelationshipDescriptor : innerJoinRelationshipDescriptors) {
				BusinessViewInnerJoinRelationship innerJoinRelationship = businessModelInitializer.addBusinessViewInnerJoinRelationship(bm,
						businessViewInnerJoinRelationshipDescriptor);
				bw.getJoinRelationships().add(innerJoinRelationship);
			}

			JSONObject jsonModel = createJson(model);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		} catch (SpagoBIException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
	}

	@POST
	@Path("/addPhysicalColumnToBusinessView")
	public Response addPhysicalColumnToBusinessView(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
			JSONObject oldJsonModel = createJson(model);

			applyDiff(jsonRoot, model);

			JSONObject json = jsonRoot.getJSONObject("data");

			BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();
			BusinessModel bm = model.getBusinessModels().get(0);
			PhysicalModel physicalModel = model.getPhysicalModels().get(0);

			BusinessView bw = bm.getBusinessViewByUniqueName(json.getString("viewUniqueName"));

			JSONArray physicaltables = json.getJSONArray("physicalTables");
			for (int i = 0; i < physicaltables.length(); i++) {
				String ptName = physicaltables.getString(i);
				PhysicalTable pt = physicalModel.getTable(ptName);
				bw.getPhysicalTables().add(pt);

				EList<PhysicalColumn> ptcol = pt.getColumns();
				for (int pci = 0; pci < ptcol.size(); pci++) {
					businessModelInitializer.addColumn(ptcol.get(pci), bw);
				}
			}

			JSONObject jsonModel = createJson(model);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		} catch (SpagoBIException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
	}

	@POST
	@Path("/deletePhysicalColumnfromBusinessView")
	public Response deletePhysicalColumnfromBusinessView(@Context HttpServletRequest req) {
		try {
			JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
			Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
			JSONObject oldJsonModel = createJson(model);

			applyDiff(jsonRoot, model);

			JSONObject json = jsonRoot.getJSONObject("data");

			BusinessModel bm = model.getBusinessModels().get(0);
			PhysicalModel physicalModel = model.getPhysicalModels().get(0);

			BusinessView bw = bm.getBusinessViewByUniqueName(json.getString("viewUniqueName"));

			PhysicalTable pt = physicalModel.getTable(json.getString("physicalTable"));
			// bw.getPhysicalTables().remove(pt);

			Iterator<BusinessColumn> colsIterator = bw.getColumns().iterator();
			while (colsIterator.hasNext()) {
				BusinessColumn businessColumn = colsIterator.next();
				if (pt.getColumn(businessColumn.getUniqueName()) != null) {
					colsIterator.remove();
				}
			}
			/*
			 * EList<PhysicalColumn> ptcol = pt.getColumns(); for (int pci = 0; pci < ptcol.size(); pci++) { bw.getColumns().remove(ptcol.get(pci)); }
			 */

			JSONObject jsonModel = createJson(model);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

			return Response.ok(patch.toString()).build();
		} catch (IOException | JSONException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		} catch (SpagoBIException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
	}

	@GET
	@Path("/modelInfos/{modelid}")
	public Response getModelInformations(@PathParam("modelid") Integer modelid, @Context HttpServletRequest req) {
		IMetaModelsDAO dao = DAOFactory.getMetaModelsDAO();
		dao.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));

		MetaModel metaModel = dao.loadMetaModelById(modelid);
		Model model = getModelWeb(metaModel.getName(), req);

		PhysicalModel physicalModel = model.getPhysicalModels().get(0);
		String schemaName = physicalModel.getSchema();
		String catalogName = physicalModel.getCatalog();

		JSONObject jsonData = new JSONObject();
		try {
			jsonData.put("schema", schemaName);
			jsonData.put("catalogName", catalogName);

		} catch (JSONException e) {
			logger.error(e);
			throw new SpagoBIServiceException(req.getPathInfo(), e);
		}
		return Response.ok(jsonData.toString()).build();

	}

	@GET
	@Path("/buildModel/{modelid}")
	public Response buildModel(@PathParam("modelid") Integer modelid, @Context HttpServletRequest req) {
		IMetaModelsDAO dao = DAOFactory.getMetaModelsDAO();
		dao.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));
		MetaModel metaModel = dao.loadMetaModelById(modelid);
		Model model = getModelWeb(metaModel.getName(), req);
		// meta model version (content)
		String modelName = req.getParameter("model");
		String schemaName = req.getParameter("schema");
		String catalogName = req.getParameter("catalog");
		String isForRegistry = req.getParameter("registry");
		String includeSourcesValue = req.getParameter("includeSources");

		BusinessModel businessModel = model.getBusinessModels().get(0);

		// set specified model name
		setModelName(businessModel, modelName);

		// set specified schema name for generation
		setSchemaName(businessModel, schemaName);

		// set specified catalog name for generation
		setCatalogName(businessModel, catalogName);

		boolean isUpdatable = Boolean.parseBoolean(isForRegistry);
		// include sources or not with the generated datamart
		boolean includeSources = Boolean.parseBoolean(includeSourcesValue);

		JpaMappingJarGenerator jpaMappingJarGenerator = new JpaMappingJarGenerator();

		logger.debug(req.getServletContext().getRealPath(File.separator));

		String libDir = req.getServletContext().getRealPath("") + File.separator + "WEB-INF" + File.separator + "lib" + File.separator;

		String filename = metaModel.getName() + ".jar";
		jpaMappingJarGenerator.setJarFileName(filename);
		ByteArrayOutputStream errorLog = new ByteArrayOutputStream();
		jpaMappingJarGenerator.setErrorLog(new PrintWriter(errorLog));
		Content content = dao.lastFileModelMeta(modelid);
		JSError errors = new JSError();
		try {
			java.nio.file.Path outDir = Files.createTempDirectory("model_");

			try {
				jpaMappingJarGenerator.generate(businessModel, outDir.toString(), isUpdatable, includeSources, new File(libDir), content.getFileModel());
			} catch (GenerationException e) {
				logger.error(e);
				errors.addErrorKey("metaWeb.generation.generic.error");
			}

			dao.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));
			content.setCreationDate(new Date());
			content.setCreationUser(getUserProfile().getUserName().toString());
			if (!errors.hasErrors()) {
				String tmpDirJarFile = outDir + File.separator + model.getBusinessModels().get(0).getName() + File.separator + "dist";
				InputStream inputStream = new FileInputStream(tmpDirJarFile + File.separator + filename);
				byte[] bytes = IOUtils.toByteArray(inputStream);

				content.setContent(bytes);
				content.setFileName(filename);

				dao.modifyMetaModelContent(modelid, content, content.getId());
			} else if (errorLog.size() > 0) {
				content.setContent(errorLog.toByteArray());
				content.setFileName(metaModel.getName() + ".log");
				dao.modifyMetaModelContent(modelid, content, content.getId());
				errors.addErrorKey("metaWeb.generation.error.log");
			}

		} catch (IOException e) {
			logger.error(e);
			errors.addErrorKey("metaWeb.generation.io.error", e.getMessage());
		} catch (AssertionError e) {
			logger.error(e);
			errors.addError(e.getMessage());
		} catch (Throwable t) {
			logger.error(t);
			errors.addErrorKey("common.generic.error");
		}
		return Response.ok(errors.toString()).build();
	}

	@POST
	@Path("/setCalculatedField")
	public Response setCalculatedBM(@Context HttpServletRequest req) throws IOException, JSONException, SpagoBIException {

		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);

		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject jsonData = jsonRoot.getJSONObject("data");
		String name = jsonData.getString("name");
		String expression = jsonData.getString("expression");
		String dataType = jsonData.getString("dataType");
		String sourceTableName = jsonData.getString("sourceTableName");
		Boolean editMode = jsonData.getBoolean("editMode");

		BusinessModel bm = model.getBusinessModels().get(0);
		BusinessColumnSet sourceBcs = bm.getBusinessTableByUniqueName(sourceTableName);
		if (sourceBcs == null) {
			// Business view
			sourceBcs = bm.getBusinessViewByUniqueName(sourceTableName);
		}
		try {
			CalculatedFieldDescriptor cfd = new CalculatedFieldDescriptor(name, expression, dataType, sourceBcs);
			BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();
			if (editMode) {
				String uniquename = jsonData.getString("uniquename");
				businessModelInitializer.editCalculatedColumn(sourceBcs.getCalculatedBusinessColumn(uniquename), cfd);
			} else {
				businessModelInitializer.addCalculatedColumn(cfd);
			}

		} catch (KnowageMetaException t) {
			return Response.ok(new JSError().addError(t.getMessage()).toString()).build();
		}

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));
		return Response.ok(patch.toString()).build();

	}

	@POST
	@Path("/deleteCalculatedField")
	public Response deleteCalculatedField(@Context HttpServletRequest req) throws IOException, JSONException, SpagoBIException {

		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String name = json.getString("name");
		String sourceTableName = json.getString("sourceTableName");

		BusinessModel bm = model.getBusinessModels().get(0);
		BusinessColumnSet sourceBcs = bm.getBusinessTableByUniqueName(sourceTableName);
		if (sourceBcs == null) {
			// Business View
			sourceBcs = bm.getBusinessViewByUniqueName(sourceTableName);
		}

		List<BusinessColumn> cbcList = sourceBcs.getColumns();
		Iterator<BusinessColumn> i = cbcList.iterator();
		while (i.hasNext()) {
			BusinessColumn column = i.next();
			if (column instanceof CalculatedBusinessColumn) {
				if (column.getName().equals(name)) {
					i.remove();
				}
			}
		}

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));
		return Response.ok(patch.toString()).build();

	}

	@POST
	@Path("/deleteBusinessClass")
	public Response deleteBusinessClass(@Context HttpServletRequest req) throws IOException, JSONException, SpagoBIException {

		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String bmName = json.getString("name");
		model.getBusinessModels().get(0).deleteBusinessTableByUniqueName(bmName);

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();

	}

	@POST
	@Path("/deleteBusinessRelation")
	public Response deleteBusinessRelation(@Context HttpServletRequest req) throws IOException, JSONException, SpagoBIException {

		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String relName = json.getString("name");
		String sourceTableName = json.getString("sourceTableName");
		String destinationTableName = json.getString("destinationTableName");

		BusinessModel businessModel = model.getBusinessModels().get(0);
		BusinessTable businessTable = businessModel.getBusinessTableByUniqueName(sourceTableName);
		BusinessRelationship businessRelationshipToRemove = null;
		if (businessTable != null) {
			List<BusinessRelationship> businessRelationships = businessTable.getRelationships();

			for (BusinessRelationship businessRelationship : businessRelationships) {
				String brSourceTableName = businessRelationship.getSourceTable().getUniqueName();
				String brDestinationTableName = businessRelationship.getDestinationTable().getUniqueName();
				String brName = businessRelationship.getUniqueName();
				if (brSourceTableName.equals(sourceTableName) && brDestinationTableName.equals(destinationTableName) && brName.equals(relName)) {
					// found relationship to be removed
					businessRelationshipToRemove = businessRelationship;
					break;
				}
			}
			if (businessRelationshipToRemove != null) {
				// remove the relationship from the model
				businessTable.getModel().getRelationships().remove(businessRelationshipToRemove);
			}
		}

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();

	}

	@POST
	@Path("/deleteBusinessView")
	public Response deleteBusinessView(@Context HttpServletRequest req) throws IOException, JSONException, SpagoBIException {
		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String bmName = json.getString("name");
		model.getBusinessModels().get(0).deleteBusinessViewByUniqueName(bmName);

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();
	}

	@GET
	@Path("/updatePhysicalModel")
	public Response updatePhysicalModel(@Context HttpServletRequest req) throws ClassNotFoundException, NamingException, SQLException, JSONException {
		PhysicalModelInitializer physicalModelInitializer = new PhysicalModelInitializer();
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);

		PhysicalModel phyMod = model.getPhysicalModels().get(0);
		DataSource dataSource = phyMod.getDataSource();
		List<String> missingTables = physicalModelInitializer.getMissingTablesNames(dataSource.getConnection(), model.getPhysicalModels().get(0));
		List<String> missingColumns = physicalModelInitializer.getMissingColumnsNames(dataSource.getConnection(), model.getPhysicalModels().get(0));
		List<String> removingItems = physicalModelInitializer.getRemovedTablesAndColumnsNames(dataSource.getConnection(), model.getPhysicalModels().get(0));
		JSONObject resp = new JSONObject();
		resp.put("missingTables", new JSONArray(JsonConverter.objectToJson(missingTables, missingTables.getClass())));
		resp.put("missingColumns", new JSONArray(JsonConverter.objectToJson(missingColumns, missingColumns.getClass())));
		resp.put("removingItems", new JSONArray(JsonConverter.objectToJson(removingItems, removingItems.getClass())));
		return Response.ok(resp.toString()).build();
	}

	@POST
	@Path("/updatePhysicalModel")
	@SuppressWarnings("unchecked")
	public Response applyUpdatePhysicalModel(@Context HttpServletRequest req) throws ClassNotFoundException, NamingException, SQLException, JSONException,
			IOException {
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		JSONObject json = RestUtilities.readBodyAsJSONObject(req);
		List<String> tables = (List<String>) JsonConverter.jsonToObject(json.getString("tables"), List.class);
		PhysicalModelInitializer physicalModelInitializer = new PhysicalModelInitializer();
		physicalModelInitializer.setRootModel(model);
		PhysicalModel originalPM = model.getPhysicalModels().get(0);
		List<String> currTables = new ArrayList<String>();
		for (PhysicalTable pt : originalPM.getTables()) {
			currTables.add(pt.getName());
		}
		currTables.addAll(tables);

		PhysicalModel phyMod = physicalModelInitializer.initializeLigth(originalPM.getConnection(), currTables);
		physicalModelInitializer.updateModel(originalPM, phyMod, tables);

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();
	}

	@POST
	@Path("/createBusinessColumn")
	public Response createBusinessColumn(@Context HttpServletRequest req) throws JsonProcessingException, SpagoBIException, IOException, JSONException {
		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String physicalTableName = json.getString("physicalTableName");
		String physicalColumnName = json.getString("physicalColumnName");
		String businessModelUniqueName = json.getString("businessModelUniqueName");
		PhysicalColumn physicalColumn = model.getPhysicalModels().get(0).getTable(physicalTableName).getColumn(physicalColumnName);
		BusinessColumnSet currBM = model.getBusinessModels().get(0).getTableByUniqueName(businessModelUniqueName);
		BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();
		businessModelInitializer.addColumn(physicalColumn, currBM);

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();
	}

	@POST
	@Path("/deleteBusinessColumn")
	public Response deleteBusinessColumn(@Context HttpServletRequest req) throws JsonProcessingException, SpagoBIException, IOException, JSONException {
		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);

		applyDiff(jsonRoot, model);

		JSONObject json = jsonRoot.getJSONObject("data");
		String businessColumnUniqueName = json.getString("businessColumnUniqueName");
		String businessModelUniqueName = json.getString("businessModelUniqueName");
		BusinessColumnSet currBM = model.getBusinessModels().get(0).getTableByUniqueName(businessModelUniqueName);
		SimpleBusinessColumn columnToDelete = currBM.getSimpleBusinessColumnByUniqueName(businessColumnUniqueName);
		columnToDelete.setIdentifier(false);
		currBM.getColumns().remove(columnToDelete);

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();
	}

	@POST
	@Path("/alterTemporalHierarchy")
	public Response alterTemporalHierarchy(@Context HttpServletRequest req) throws JsonProcessingException, SpagoBIException, IOException, JSONException {
		JSONObject jsonRoot = RestUtilities.readBodyAsJSONObject(req);
		Model model = (Model) req.getSession().getAttribute(EMF_MODEL);
		JSONObject oldJsonModel = createJson(model);
		applyDiff(jsonRoot, model);
		JSONObject json = jsonRoot.getJSONObject("data");

		String businessModelUniqueName = json.getString("businessModelUniqueName");
		JSONArray hierarchy = json.getJSONArray("hierarchy");

		OlapModelInitializer omInit = new OlapModelInitializer();
		if (model.getOlapModels().size() == 0) {
			omInit.setRootModel(model);
			omInit.initialize(model.getName());
		}
		OlapModel olapModel = model.getOlapModels().get(0);

		// remove the dimension associated if present
		Iterator<Dimension> dimIter = olapModel.getDimensions().iterator();
		while (dimIter.hasNext()) {
			Dimension dim = dimIter.next();
			if (dim.getTable().getUniqueName().equals(businessModelUniqueName)) {
				dimIter.remove();
				break;
			}
		}

		// create the new dimension

		BusinessColumnSet currBM = model.getBusinessModels().get(0).getTableByUniqueName(businessModelUniqueName);
		Dimension dim = omInit.addDimension(olapModel, currBM);

		// create the hierarchy
		for (int i = 0; i < hierarchy.length(); i++) {
			JSONObject obj = hierarchy.getJSONObject(i);
			HierarchyDescriptor hierarchyDescriptor = new HierarchyDescriptor();
			hierarchyDescriptor.setName(obj.getString("name"));
			JSONObject prop = obj.optJSONObject("properties");
			if (prop != null) {
				if (prop.has("hasall")) {
					hierarchyDescriptor.setHasAll(prop.getBoolean("hasall"));
				}
				if (prop.has("defaultHierarchy")) {
					hierarchyDescriptor.setDefaultHierarchy(prop.getBoolean("defaultHierarchy"));
				}
				if (prop.has("allmembername")) {
					hierarchyDescriptor.setAllMemberName(prop.getString("allmembername"));
				}
			}

			Hierarchy hie = omInit.addHierarchy(dim, hierarchyDescriptor);

			if (obj.has("levels")) {
				JSONArray levels = obj.getJSONArray("levels");
				for (int l = 0; l < levels.length(); l++) {
					JSONObject tmplev = levels.getJSONObject(l);
					HierarchyLevelDescriptor levelDescriptor = new HierarchyLevelDescriptor();
					levelDescriptor.setName(tmplev.getString("name"));
					levelDescriptor.setBusinessColumn(currBM.getSimpleBusinessColumnByUniqueName(tmplev.getJSONObject("column").getString("uniqueName")));
					if (tmplev.has("properties")) {
						for (int pr = 0; pr < tmplev.getJSONArray("properties").length(); pr++) {
							JSONObject tmpPro = tmplev.getJSONArray("properties").getJSONObject(pr);
							if (tmpPro.getString("key").equals(OlapModelPropertiesFromFileInitializer.LEVEL_TYPE)) {
								levelDescriptor.setLevelType(tmpPro.getJSONObject("value").getString("value"));
							}
						}
					} else if (tmplev.has("leveltype")) {
						levelDescriptor.setLevelType(tmplev.getString("leveltype"));
					}
					Level lev = omInit.addHierarchyLevel(hie, levelDescriptor);

				}

			}

		}

		JSONObject jsonModel = createJson(model);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode patch = JsonDiff.asJson(mapper.readTree(oldJsonModel.toString()), mapper.readTree(jsonModel.toString()));

		return Response.ok(patch.toString()).build();
	}

	private void setModelName(BusinessModel businessModel, String modelName) {
		if ((modelName != null) && (modelName.length() > 0)) {
			businessModel.setName(modelName);
		}
	}

	private void setSchemaName(BusinessModel businessModel, String schemaName) {
		if ((schemaName != null) && (schemaName.length() > 0)) {
			PhysicalModel physicalModel = businessModel.getPhysicalModel();
			physicalModel.setSchema(schemaName);
		}

	}

	private void setCatalogName(BusinessModel businessModel, String catalogName) {
		if ((catalogName != null) && (catalogName.length() > 0)) {
			PhysicalModel physicalModel = businessModel.getPhysicalModel();
			physicalModel.setCatalog(catalogName);
		}
	}

	private void applyBusinessClass(JsonNode newBM, Model model) {
		String name = newBM.get("name").textValue();
		String description = newBM.get("description").textValue();
		String table = newBM.get("physicalModel").textValue();
		Iterator<JsonNode> colIterator = newBM.get("selectedColumns").elements();

		BusinessModel bm = model.getBusinessModels().get(0);

		BusinessTable bt = BusinessModelFactory.eINSTANCE.createBusinessTable();
		PhysicalTable pt = model.getPhysicalModels().get(0).getTable(table);
		bt.setModel(bm);
		bm.getBusinessTables().add(bt);
		bt.setName(name);
		bt.setUniqueName(name.toLowerCase().replaceAll(" ", "_"));
		bt.setDescription(description);
		bt.setPhysicalTable(pt);
		bt.setDescription(description);
		BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();

		while (colIterator.hasNext()) {
			JsonNode col = colIterator.next();
			String colName = col.textValue();
			businessModelInitializer.addColumn(pt.getColumn(colName), bt);
		}

		// adding table identifier if requested
		if (pt.getPrimaryKey() != null) {
			businessModelInitializer.addIdentifier(bt, bm);
		}

		businessModelInitializer.getPropertiesInitializer().addProperties(bt);

		// add outcome relationship
		List<PhysicalForeignKey> physicalForeignKeys = bt.getPhysicalTable().getForeignKeys();
		for (PhysicalForeignKey foreignKey : physicalForeignKeys) {
			for (BusinessTable businessTable : bm.getBusinessTables()) {
				businessModelInitializer.addRelationship(bt, businessTable, foreignKey);
			}
		}

		// add income relationship
		for (BusinessTable businessTable : bm.getBusinessTables()) {
			if (businessTable == bt)
				continue;
			List<PhysicalForeignKey> foreignKeys = businessTable.getPhysicalTable().getForeignKeys();
			for (PhysicalForeignKey foreignKey : foreignKeys) {
				businessModelInitializer.addRelationship(businessTable, bt, foreignKey);
			}
		}

	}

	private Model getModelWeb(String modelName, HttpServletRequest req) {
		IMetaModelsDAO businessModelsDAO = DAOFactory.getMetaModelsDAO();
		businessModelsDAO.setUserProfile((IEngUserProfile) req.getSession().getAttribute(IEngUserProfile.ENG_USER_PROFILE));
		Content metaModelContent = businessModelsDAO.loadActiveMetaModelWebContentByName(modelName);
		ByteArrayInputStream bis = new ByteArrayInputStream(metaModelContent.getFileModel());
		EmfXmiSerializer serializer = new EmfXmiSerializer();
		return serializer.deserialize(bis);
	}

	private void applyRelationships(JsonNode rel, BusinessModel bm) {
		BusinessRelationship br = BusinessModelFactory.eINSTANCE.createBusinessRelationship();
		bm.getRelationships().add(br);
		String uniqueName = rel.get("uniqueName").textValue();

		String sourceTableName = rel.get("sourceTableName").asText().toLowerCase();
		String destinationTableName = rel.get("destinationTableName").asText().toLowerCase();
		BusinessColumnSet sourceBcs = bm.getBusinessTableByUniqueName(sourceTableName);
		if (sourceBcs == null) {
			// check if is a business view
			sourceBcs = bm.getBusinessViewByUniqueName(sourceTableName);
		}

		br.setSourceTable(sourceBcs);

		BusinessColumnSet destBcs = bm.getBusinessTableByUniqueName(destinationTableName);
		if (destBcs == null) {
			// check if is a business view
			destBcs = bm.getBusinessViewByUniqueName(destinationTableName);
		}
		br.setDestinationTable(destBcs);

		if (rel.has("id"))
			br.setId(rel.get("id").textValue());
		if (rel.has("description"))
			br.setDescription(rel.get("description").textValue());
		if (rel.has("name"))
			br.setName(rel.get("name").textValue());

		br.setUniqueName(uniqueName);

		Iterator<JsonNode> sourceColsIterator = rel.get("sourceColumns").elements();
		while (sourceColsIterator.hasNext()) {
			JsonNode jsonNode = sourceColsIterator.next();
			BusinessColumn bc;
			if (jsonNode.isTextual()) {
				bc = sourceBcs.getSimpleBusinessColumnByUniqueName(jsonNode.asText());
			} else {
				bc = sourceBcs.getSimpleBusinessColumnByUniqueName(jsonNode.get("uniqueName").asText());
			}
			br.getSourceColumns().add(bc);
		}
		Iterator<JsonNode> destColsIterator = rel.get("destinationColumns").elements();
		while (destColsIterator.hasNext()) {
			JsonNode jsonNode = destColsIterator.next();
			BusinessColumn bc;
			if (jsonNode.isTextual()) {
				bc = destBcs.getSimpleBusinessColumnByUniqueName(jsonNode.asText());
			} else {
				bc = destBcs.getSimpleBusinessColumnByUniqueName(jsonNode.get("uniqueName").asText());
			}
			br.getDestinationColumns().add(bc);
		}
		// setting multiplicity and other properties
		if (rel.has("properties")) {
			Iterator<JsonNode> propIterator = rel.get("properties").elements();
			while (propIterator.hasNext()) {
				JsonNode jsonNode = propIterator.next();
				ModelPropertyType propType = bm.getPropertyType(jsonNode.get("key").textValue());
				br.setProperty(propType, jsonNode.get("value").textValue());
			}
		}
		// This field should be null (in relationship) so it is left unset
		// br.setPhysicalForeignKey(null);
	}

	private void applyDiff(JSONObject jsonRoot, Model model) throws SpagoBIException, JsonProcessingException, IOException, JSONException {
		if (jsonRoot.has("diff")) {
			JsonNode patch = new ObjectMapper().readTree(jsonRoot.getString("diff"));
			applyPatch(patch, model);
		}
	}

	private void applyPatch(JsonNode patch, Model model) throws SpagoBIException {
		logger.debug("applyPatch:" + patch != null ? patch.toString() : "null");
		Iterator<JsonNode> elements = patch.elements();
		JXPathContext context = JXPathContext.newContext(model);
		context.setFactory(new ModelPropertyFactory());
		while (elements.hasNext()) {
			JsonNode jsonNode = elements.next();
			String operation = jsonNode.get("op").textValue();
			String path = jsonNode.get("path").textValue();

			if (toSkip(path, operation)) {
				logger.debug("skipping " + path);
				continue;
			}

			path = cleanPath(path);

			switch (operation.toUpperCase()) {
			case "ADD":
				JsonNode node = jsonNode.get("value");
				logger.debug("add " + node.asText());
				addJson(node, path, context);
				break;
			case "REMOVE":
				remove(path, context);
				break;
			case "REPLACE":
				String value = jsonNode.get("value").asText();
				replace(value, path, context);
				break;
			case "MOVE":
				String from = cleanPath(jsonNode.get("from").textValue());
				logger.debug("moving from [" + from + "] to [" + path + "]");
				Object obj = context.getPointer(from).getNode();
				remove(from, context);
				// addValue(obj, path, context);
				replace(obj, path, context);
				break;
			default:
				throw new SpagoBIException("invalid json patch operation [" + operation + "]");
			}
		}
	}

	private boolean toSkip(String path, String operation) {
		if (path.equals("/datasourceId") || path.equals("/modelName") || path.equals("/physicalModels") || path.equals("/businessModels")
				|| path.contains("/physicalColumn/") || path.contains("/simpleBusinessColumns")
				|| (path.contains("relationships") && !operation.equals("remove")) || path.contains("referencedColumns")) {
			logger.debug("skipping " + path);
			return true;
		}
		return false;
	}

	private void replace(Object value, String path, JXPathContext context) throws SpagoBIException {
		try {
			if (value instanceof TextNode)
				value = ((TextNode) value).asText();
			if (value == null || value.equals("null") || value instanceof NullNode)
				value = null;
			context.createPathAndSetValue(path, value);
		} catch (Throwable t) {
			t.printStackTrace();
			throw new SpagoBIException("Error in replace", t);
		}
	}

	private void remove(String path, JXPathContext context) throws SpagoBIException {
		if (path.matches(".*\\]$")) {
			int i = path.lastIndexOf("[");
			Pointer obj = context.getPointer(path);
			logger.debug("REMOVE > " + path + " > " + obj.getNode());
			Pointer coll = context.getPointer(path.substring(0, i));
			if (obj.getNode() instanceof SimpleBusinessColumnImpl) {
				((SimpleBusinessColumnImpl) obj.getNode()).setIdentifier(false);
			}
			if (obj.getNode() instanceof BusinessRelationshipImpl) {
				((BusinessRelationshipImpl) obj.getNode()).removeRelationship();
				return;
			}

			((List<?>) coll.getNode()).remove(obj.getNode());
		} else {
			// TODO
			// throw new SpagoBIException("TODO operation \"remove\" not supported for single element");
		}
	}

	private void addJson(JsonNode obj, String topath, JXPathContext context) throws SpagoBIException {
		if (toSkip(topath, "add")) {
			logger.debug("skipping " + topath);
			return;
		}

		if (obj.isArray()) {
			logger.debug("isArray! " + obj);
			int i = 1;
			for (JsonNode jsonNode : obj) {
				addJson(jsonNode, topath + "[" + i + "]", context);
			}
		} else if (obj.isObject()) {

			// setting all key-values from json to eObject
			Iterator<Entry<String, JsonNode>> elementIterator = obj.fields();
			while (elementIterator.hasNext()) {
				Entry<String, JsonNode> ele = elementIterator.next();
				addJson(ele.getValue(), topath + "/" + ele.getKey(), context);
			}
		} else {
			addValue(obj.textValue(), topath, context);
		}

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void addValue(String obj, String topath, JXPathContext context) throws SpagoBIException {
		logger.debug("ADD > " + obj + " > " + topath);
		if (topath.endsWith("]")) {
			int i = topath.lastIndexOf("[");
			Pointer toColl = context.getPointer(topath.substring(0, i));
			if (toColl.getNode() instanceof List) {
				if (!((List) toColl.getNode()).contains(obj)) {
					((List) toColl.getNode()).add(obj);
				}
			}
		} else {
			replace(obj, topath, context);
		}
	}

	/**
	 * Used to convert a path coming from a frontend jsonDiff in a path as expected by jxpath. Frontend model is something like {physicalModels:[...],
	 * businessModels:[...]} and it has to be converted to backend model structure that is something like
	 * {businessModels:[tables:[...]],businessModels:[businessTables:[...]]} Furthermore jsonDiff is zero-based numbering but jxpath is 1-based numbering
	 * Another difference is that jsonDiff's notation used to select a property of a nth element of a collection is "parent/n/property" but jxpath does same
	 * selection in this way "parent[n]/property"
	 *
	 * @param path
	 * @return path cleaned
	 */
	private String cleanPath(String path) {
		path = path.replaceAll("^/physicalModels", "/businessModels/0/tables").replaceAll("^/businessModels", "/businessModels/0/businessTables");
		Pattern p = Pattern.compile("(/)(\\d+)");
		Matcher m = p.matcher(path);
		StringBuffer s = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(s, "[" + String.valueOf(1 + Integer.parseInt(m.group(2))) + "]");
		}
		m.appendTail(s);
		return s.toString();
	}

	public static JSONObject createJson(Model model) throws JSONException {
		JSONObject translatedModel = new JSONObject();
		Map<String, Integer> physicalTableMap = new HashMap<>();

		JSONArray physicalModelJson = new JSONArray();
		EList<PhysicalTable> physicalTables = model.getPhysicalModels().get(0).getTables();
		for (int j = 0; j < physicalTables.size(); j++) {
			PhysicalTable physicalTable = physicalTables.get(j);
			physicalModelJson.put(new JSONObject(JsonConverter.objectToJson(physicalTable, physicalTable.getClass())));
			physicalTableMap.put(physicalTable.getName(), j);
		}

		JSONArray businessModelJson = new JSONArray();
		Iterator<BusinessTable> businessModelsIterator = model.getBusinessModels().get(0).getBusinessTables().iterator();
		while (businessModelsIterator.hasNext()) {
			BusinessTable curr = businessModelsIterator.next();
			String tabelName = curr.getPhysicalTable().getName();
			JSONObject bmJson = new JSONObject(JsonConverter.objectToJson(curr, curr.getClass()));
			bmJson.put("physicalTable", new JSONObject().put("physicalTableIndex", physicalTableMap.get(tabelName)));
			businessModelJson.put(bmJson);
		}

		JSONArray businessViewJson = new JSONArray();
		List<BusinessView> businessViews = model.getBusinessModels().get(0).getBusinessViews();
		for (int j = 0; j < businessViews.size(); j++) {
			BusinessView businessView = businessViews.get(j);
			JSONObject bcJson = new JSONObject(JsonConverter.objectToJson(businessView, businessView.getClass()));

			List<PhysicalTable> ptList = businessView.getPhysicalTables();
			JSONArray ptL = new JSONArray();
			for (PhysicalTable pt : ptList) {
				ptL.put(new JSONObject().put("physicalTableIndex", physicalTableMap.get(pt.getName())));
			}
			bcJson.put("physicalTables", ptL);
			businessViewJson.put(bcJson);
		}
		JSONArray olapModelJson = new JSONArray();
		Iterator<OlapModel> olapIterator = model.getOlapModels().iterator();
		while (olapIterator.hasNext()) {
			OlapModel olapModel = olapIterator.next();
			JSONObject omJson = new JSONObject(JsonConverter.objectToJson(olapModel, olapModel.getClass()));

			JSONArray dimension = omJson.getJSONArray("dimensions");
			for (int i = 0; i < dimension.length(); i++) {
				JSONObject currDim = dimension.getJSONObject(i);
				currDim.put("table", currDim.getJSONObject("table").getString("uniqueName"));

				JSONArray hier = currDim.getJSONArray("hierarchies");
				for (int h = 0; h < hier.length(); h++) {
					JSONObject currHier = hier.getJSONObject(h);
					currHier.put("table", currHier.getJSONObject("table").getString("uniqueName"));
				}

			}

			olapModelJson.put(omJson);
		}
		translatedModel.put("physicalModels", physicalModelJson);
		translatedModel.put("businessModels", businessModelJson);
		translatedModel.put("businessViews", businessViewJson);
		translatedModel.put("olapModels", olapModelJson);
		return translatedModel;
	}

	@SuppressWarnings("unchecked")
	private Model createEmptyModel(JSONObject json) throws JSONException {
		Assert.assertTrue(json.has("datasourceId"), "datasourceId is mandatory");
		Assert.assertTrue(json.has("physicalModels"), "physicalModels is mandatory");
		Assert.assertTrue(json.has("businessModels"), "businessModels is mandatory");

		Integer dsId = json.getInt("datasourceId");
		String modelName = json.optString("modelName", DEFAULT_MODEL_NAME);
		List<String> physicalModels = (List<String>) JsonConverter.jsonToObject(json.getString("physicalModels"), List.class);
		List<String> businessModels = (List<String>) JsonConverter.jsonToObject(json.getString("businessModels"), List.class);

		Model model = ModelFactory.eINSTANCE.createModel();
		model.setName(modelName);

		PhysicalModelInitializer physicalModelInitializer = new PhysicalModelInitializer();
		physicalModelInitializer.setRootModel(model);
		PhysicalModel physicalModel = physicalModelInitializer.initialize(dsId, physicalModels);

		List<PhysicalTable> physicalTableToIncludeInBusinessModel = new ArrayList<PhysicalTable>();
		for (int i = 0; i < businessModels.size(); i++) {
			physicalTableToIncludeInBusinessModel.add(physicalModel.getTable(businessModels.get(i)));
		}
		PhysicalTableFilter physicalTableFilter = new PhysicalTableFilter(physicalTableToIncludeInBusinessModel);
		BusinessModelInitializer businessModelInitializer = new BusinessModelInitializer();
		businessModelInitializer.initialize(modelName, physicalTableFilter, physicalModel);

		return model;
	}

	@SuppressWarnings("rawtypes")
	public static byte[] extractSbiModelFromJar(Content content) {
		byte[] contentBytes = content.getContent();

		JarFile jar = null;
		FileOutputStream output = null;
		java.io.InputStream is = null;

		try {
			UUIDGenerator uuidGen = UUIDGenerator.getInstance();
			UUID uuidObj = uuidGen.generateTimeBasedUUID();
			String idCas = uuidObj.toString().replaceAll("-", "");
			logger.debug("create temp file for jar");
			String path = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + idCas + ".jar";
			logger.debug("temp file for jar " + path);
			File filee = new File(path);
			output = new FileOutputStream(filee);
			IOUtils.write(contentBytes, output);

			jar = new JarFile(filee);
			logger.debug("jar file created ");

			Enumeration enumEntries = jar.entries();
			while (enumEntries.hasMoreElements()) {
				JarEntry fileEntry = (java.util.jar.JarEntry) enumEntries.nextElement();
				logger.debug("jar content " + fileEntry.getName());

				if (fileEntry.getName().endsWith("sbimodel")) {
					logger.debug("found model file " + fileEntry.getName());
					is = jar.getInputStream(fileEntry);
					byte[] byteContent = SpagoBIUtilities.getByteArrayFromInputStream(is);
					return byteContent;

				}

			}
		} catch (IOException e1) {
			logger.error("the model file could not be taken by datamart.jar due to error ", e1);
			return null;
		} finally {
			try {

				if (jar != null)
					jar.close();
				if (output != null)
					output.close();
				if (is != null)
					is.close();
			} catch (IOException e) {
				logger.error("error in closing streams");
			}
			logger.debug("OUT");
		}
		logger.error("the model file could not be taken by datamart.jar");
		return null;

	}

}

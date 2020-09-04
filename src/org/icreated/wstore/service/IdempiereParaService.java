/*******************************************************************************
 * @author Copyright (C) 2019 ICreated, Sergey Polyarus
 *  @date 2019
 *  This program is free software; you can redistribute it and/or modify it
 *  under the terms version 2 of the GNU General Public License as published
 *  by the Free Software Foundation. This program is distributed in the hope
 *  that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc., 
 *  59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 ******************************************************************************/
package org.icreated.wstore.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.ws.rs.core.Response;

import org.adempiere.model.GenericPO;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.GridWindow;
import org.compiere.model.GridWindowVO;
import org.compiere.model.MColumn;
import org.compiere.model.MRole;
import org.compiere.model.MTab;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.MUserRoles;
import org.compiere.model.MWebServiceType;
import org.compiere.model.MWindow;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_WS_WebServiceFieldInput;
import org.compiere.model.X_WS_WebService_Para;
import org.compiere.util.CLogger;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.icreated.wstore.bean.SessionUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class IdempiereParaService extends AService {
	
	
	CLogger log = CLogger.getCLogger(IdempiereParaService.class);
		
	private final String API_WEB_SERVICE 	  = "RESTapi";
	private final String serviceMethod_GET 	  = "GET";
	private final String serviceMethod_POST   = "POST";
	private final String serviceMethod_PUT 	  = "PUT";
	private final String serviceMethod_DELETE = "DELETE";
	
	public IdempiereParaService(Properties ctx, SessionUser user) {
		
		this.ctx = ctx;
		this.sessionUser = user;
		Env.setCtx(ctx);
	}
	
	public  Response doSearchResp(String searchString, Map<String, Object> bodyJson) {
		
		String listSearch_para[] = searchString.split("_"); 
		MWebServiceType webREST = new Query(ctx, MWebServiceType.Table_Name, "Value=?", null)
				.setClient_ID()
				.setOnlyActiveRecords(true)
				.setParameters(listSearch_para[0])
				.first();
		
		//check
		if(webREST!=null) {
			if(webREST.getWS_WebService().getValue().equalsIgnoreCase(API_WEB_SERVICE)) {
				Response finalResponse = methodService(webREST, searchString, bodyJson);
				return finalResponse;
			}
		}
		
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
	}


	private Response methodService(MWebServiceType webREST, String searchString, Map<String, Object> bodyJson) {
		Response resp = null;
		Object[] para = searchString.split("_");
		List<Object> list = new ArrayList<Object>(Arrays.asList(para));
		String colInput[] = webREST.getInputColumnNames(true);
		String colOutput[] = webREST.getOutputColumnNames(true);
		String tableName = MTable.get(ctx, webREST.getAD_Table_ID(), null).getTableName();
		StringBuilder sqlWhere = new StringBuilder();
		///---- ADD Where
		String addWhere = "";
		X_WS_WebService_Para paraStatic = webREST.getParameter("Filter");
		if(paraStatic!=null && X_WS_WebService_Para.PARAMETERTYPE_Constant.equals(paraStatic.getParameterType()))
			addWhere = paraStatic.getConstantValue();
		///----  ----------
		int count = 0;
		/// Se oltre alla chiamata_REST c'è qualche altro parametro di ricerca, allora creo query di filtro......
		if(list.size()>1) {  
			for (String input_C : colInput) {
				count = count+1;
				if(count > 1)
					sqlWhere.append(" AND ");
				X_WS_WebServiceFieldInput inputField = webREST.getFieldInput(input_C);
				MColumn col = MColumn.get(ctx, inputField.getAD_Column_ID());		    			
				String sqlType = DisplayType.getSQLDataType(col.getAD_Reference_ID(), col.getColumnName(), col.getFieldLength());
				if(sqlType.contains("CHAR"))
					sqlWhere.append(input_C).append(" LIKE ?");
				else {
					sqlWhere.append(input_C).append("=?");
					if (DisplayType.isID(col.getAD_Reference_ID()))
						list.set((count), Integer.parseInt((String)list.get(count)));
					else if (DisplayType.isNumeric (col.getAD_Reference_ID ()))
						list.set((count), new BigDecimal((String)list.get(count)));
					else if (DisplayType.isDate(col.getAD_Reference_ID()))
						list.set((count), Timestamp.valueOf((String)list.get(count)));
				}
			}
		}
		/// -----
		if(!addWhere.isEmpty()) {
			if(sqlWhere.toString().isEmpty())
				sqlWhere.append(addWhere);
			else
				sqlWhere.append(" AND ").append(addWhere);
		}
		
		Query qry = null;
		String method = webREST.getWS_WebServiceMethod().getValue();
		switch (method) {
		case serviceMethod_GET:
			if(colOutput.length>0) {
				qry = new Query(ctx, tableName, sqlWhere.toString(), null)
						.setClient_ID()
						.setOnlyActiveRecords(true);
				if(sqlWhere!= null && !sqlWhere.toString().trim().isEmpty()) {
					List<Object> listUpdate = list;
					listUpdate.remove(0);
					qry= qry.setParameters(listUpdate);
				}
				List<PO> poList = qry.list();
				List<Map<String, Object>> array = new ArrayList<Map<String,Object>>();
				Map<String, Object> jsonNode = null;
				for (PO po : poList) {
					jsonNode = new TreeMap<String, Object>();
					for (String output_C : colOutput) {
						if(output_C.equalsIgnoreCase(tableName+"_ID"))
							jsonNode.put("id", po.get_Value(output_C));
						else
							jsonNode.put(output_C, po.get_Value(output_C));
					}
					array.add(jsonNode);
				}
				
				ObjectMapper mapper = new ObjectMapper();
				try {
					String jsonFinal = mapper.writeValueAsString(array);
					resp = Response.status(Response.Status.ACCEPTED).entity(jsonFinal).build();
				} catch (JsonProcessingException e) {
					resp = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\""+e.getMessage()+"\"}").build();   e.printStackTrace();
				}
			}
			
			break;
		case serviceMethod_PUT:
			if(bodyJson!=null && bodyJson.size()>0) {
				qry = new Query(ctx, tableName, tableName+"_ID=?", null)
						.setClient_ID()
						.setOnlyActiveRecords(true)
						.setParameters(bodyJson.get("id"));
				PO po = qry.first();
				if(po!=null) {
					for (String input_C : colInput) {
						if(input_C.equalsIgnoreCase(tableName+"_ID")) //scarto la colonna ID della tabella....
							continue;
						
						po.set_ValueOfColumn(input_C, bodyJson.get(input_C));
					}
					po.getCtx().put("#AD_Client_ID", po.get_Value("AD_client_ID"));
					if(po.save())
						resp = Response.status(Response.Status.ACCEPTED).entity("{\"message\":\"model aggiornato\"}").build();
					else 
						resp = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"model NON aggiornato\"}").build();	
				}
			}
			
			break;
		case serviceMethod_POST:
			if(bodyJson!=null && bodyJson.size()>0 && webREST.get_ValueAsInt("AD_Tab_ID")>0) {
				MUser mUser = MUser.get(ctx, sessionUser.getAD_User_ID());
				MUserRoles usRole = new Query(ctx, MUserRoles.Table_Name, "AD_User_ID=? AND AD_Client_ID=?", null)
						.setOnlyActiveRecords(true)
						.setParameters(mUser.getAD_User_ID(),mUser.getAD_Client_ID())
						.setOrderBy("AD_Role_ID")
						.first();
				ctx.setProperty("#AD_Role_ID", String.valueOf(usRole.getAD_Role_ID()));
				ctx.setProperty("#AD_User_ID", String.valueOf(sessionUser.getAD_User_ID()));
				MTab tab = new MTab(ctx, webREST.get_ValueAsInt("AD_Tab_ID"), null);
				
				GridWindowVO gwVO = GridWindowVO.create(ctx, -1, tab.getAD_Window_ID());
				GridWindow m_mWindow = new GridWindow(gwVO, true);
				m_mWindow.initTab(0);
				GridTab m_mTab = m_mWindow.getTab(0);
				GridField[] l_gridFields = m_mTab.getFields();
				List<String> listCol = new ArrayList<String>(Arrays.asList(colInput));
				
				GenericPO po = new GenericPO(tableName, ctx, 0);
				for (String input_C : listCol) {
					po.set_ValueOfColumn(input_C, bodyJson.get(input_C));
				}
				//set value Default_Mandatory
				for (GridField gridField : l_gridFields) {
					if(gridField.isMandatory(true) && !listCol.contains(gridField.getColumnName())) {
						po.set_ValueOfColumn(gridField.getColumnName(), gridField.getDefault());
					}
				}
				
				po.getCtx().put("#AD_Client_ID", po.get_Value("AD_client_ID"));
				if(po.save())
					resp = Response.status(Response.Status.ACCEPTED).entity("{\"message\":\"model inserito\"}").build();
				else 
					resp = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"model NON inserito\"}").build();	
			}
			
			break;
		case serviceMethod_DELETE:
			if(colInput.length>0 && para.length>1) {
				List<Object> listUpdate = list;
				listUpdate.remove(0); // rimuovo la stringa della chiamata REST
				qry = new Query(ctx, tableName, sqlWhere.toString(), null)
					.setClient_ID()
					.setOnlyActiveRecords(true)
					.setParameters(listUpdate);
				PO po = qry.first();
				if(po!=null) {
					//delete
					po.getCtx().put("#AD_Client_ID", po.get_Value("AD_client_ID"));
					if(po.delete(true))
						resp = Response.status(Response.Status.ACCEPTED).entity("{\"message\":\"DELETE id _"+list.get(0)+"\"}").build();
					else 
						resp = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();						
				}
			}
			break;
		default:
			break;
		}
		
		return resp;
	}


}

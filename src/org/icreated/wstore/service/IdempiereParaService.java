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
import java.net.URI;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.adempiere.model.GenericPO;
import org.adempiere.util.ProcessUtil;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.GridWindow;
import org.compiere.model.GridWindowVO;
import org.compiere.model.MColumn;
import org.compiere.model.MPInstance;
import org.compiere.model.MPInstanceLog;
import org.compiere.model.MPInstancePara;
import org.compiere.model.MProcess;
import org.compiere.model.MProcessPara;
import org.compiere.model.MTab;
import org.compiere.model.MTable;
import org.compiere.model.MUser;
import org.compiere.model.MUserRoles;
import org.compiere.model.MWebServiceType;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_AD_PInstance_Log;
import org.compiere.model.X_WS_WebServiceFieldInput;
import org.compiere.model.X_WS_WebService_Para;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.icreated.wstore.bean.SessionUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
					sqlWhere.append("LOWER("+input_C+")").append(" LIKE LOWER(?)");
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
			else {
				X_WS_WebService_Para param_ = webREST.getParameter("AD_Process_UU");
				if(param_!=null && X_WS_WebService_Para.PARAMETERTYPE_Constant.equals(param_.getParameterType())
						&& bodyJson!=null && !bodyJson.isEmpty()) {
					int idProcess = DB.getSQLValue(null, "SELECT AD_Process_ID FROM AD_Process WHERE AD_PROCESS_UU=?", param_.getConstantValue());
					MProcess proc = new MProcess(ctx, idProcess, null);
					MPInstance instance = new MPInstance(proc, 0);
				    instance.saveEx();
				    ProcessInfo poInfo = new ProcessInfo(proc.getName(), proc.getAD_Process_ID());
				    if(bodyJson.containsKey("ids")) {
				    	poInfo.setRecord_IDs(((List<Integer>) bodyJson.get("ids")));
				    }
				    //poInfo.setRecord_ID(1000003);
				    poInfo.setAD_Process_ID(proc.getAD_Process_ID());
				    poInfo.setAD_PInstance_ID(instance.getAD_PInstance_ID());
				    poInfo.setAD_Process_UU(proc.getAD_Process_UU());
				    poInfo.setClassName(proc.getClassname());
				    ArrayList<ProcessInfoParameter> listPP = new ArrayList<ProcessInfoParameter>();
				    MProcessPara[] p_parameter = proc.getParameters();
				    MProcessPara paraTmp = null;
				    int reference_ID = -1;
				    for (Map.Entry<String, Object> entry : bodyJson.entrySet()) {
				    	if(p_parameter.length>0) {
				    		paraTmp = Arrays.stream(p_parameter).filter(mpp -> mpp.getColumnName().equals(entry.getKey()))
				    				.findAny().orElse(null);
				    		reference_ID = (paraTmp==null)?-1:paraTmp.getAD_Reference_ID();
				    		if(reference_ID>0) {
				    			if(DisplayType.isDate(reference_ID)) {
				    				listPP.add(new ProcessInfoParameter(entry.getKey(), Timestamp.valueOf((String)entry.getValue()), null, null, null));
				    				reference_ID = -1;
				    				continue;
				    			}
				    			else if(DisplayType.isNumeric (reference_ID)) {
				    				listPP.add(new ProcessInfoParameter(entry.getKey(), new BigDecimal((String)entry.getValue()), null, null, null));
				    				reference_ID = -1;
				    				continue;
				    			}
				    			reference_ID = -1;
				    		}
				    	}
				    	listPP.add(new ProcessInfoParameter(entry.getKey(), entry.getValue(), null, null, null));
				    }
				    ProcessInfoParameter[] pars = new ProcessInfoParameter[listPP.size()];
				    listPP.toArray(pars);
				    poInfo.setParameter(pars);
				    if(bodyJson.containsKey("ids")) {
					    DB.createT_Selection(poInfo.getAD_PInstance_ID(), poInfo.getRecord_IDs(), null);
						MPInstancePara ip = instance.createParameter(-1, "*RecordIDs*", poInfo.getRecord_IDs().toString());
						ip.saveEx();
				    }
				    //poInfo.setTransientObject(bodyJson);
				    ProcessUtil.startJavaProcess(ctx, poInfo, null);
				    String txt = poInfo.getSummary();
				    if(txt!=null && !txt.isEmpty())
				    	resp = Response.status(Response.Status.ACCEPTED).entity("{\"cod\":\"WARN\", \"message\":[{\"msg\": \""+txt+"\", \"link\": \"null\"}]}").build();
				    else {
				    	MPInstance insta = new MPInstance(ctx, poInfo.getAD_PInstance_ID(), null);
				    	if(insta!= null && insta.getLog().length>0) {
				    		MPInstanceLog[] logs = insta.getLog();
				    		List<X_AD_PInstance_Log> array = new Query(ctx, X_AD_PInstance_Log.Table_Name, "AD_PInstance_ID=?", null)
				    				.setParameters(poInfo.getAD_PInstance_ID())
				    				.list();
				    		StringBuilder respLink = new StringBuilder();
				    		UriInfo infoURI = (UriInfo) bodyJson.get("URI");
				    		URI url = infoURI.getBaseUri();
				    		int countLog = 0;
				    		for (X_AD_PInstance_Log lg : array) {
				    			if(countLog>0)
				    				respLink.append(",");
				    			respLink.append("{").append("\"msg\": \""+lg.getP_Msg()+"\"");
				    			respLink.append(", \"link\": \"");
				    			if(lg.getAD_Table_ID()>0 && lg.getRecord_ID()>0)
				    				respLink.append("http://"+url.getAuthority()+"/webui/index.zul?Action=Zoom&AD_Table_ID=" + lg.getAD_Table_ID() + "&Record_ID=" + lg.getRecord_ID());
				    			else
				    				respLink.append("null");
				    			respLink.append("\"}");
				    			countLog++;
							}
				    		resp = Response.status(Response.Status.ACCEPTED).entity("{\"cod\":\"OK\",\"message\":["+respLink+"]}").build();
				    	}
				    }
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
						
						X_WS_WebServiceFieldInput inputField = webREST.getFieldInput(input_C);
						MColumn col = MColumn.get(ctx, inputField.getAD_Column_ID());
						if (DisplayType.isDate(col.getAD_Reference_ID()))
							po.set_ValueOfColumn(input_C, Timestamp.valueOf((String)bodyJson.get(input_C)));
						else if (DisplayType.isNumeric (col.getAD_Reference_ID ()))
							po.set_ValueOfColumn(input_C, new BigDecimal((String)bodyJson.get(input_C)));
						else
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
				po.set_ValueOfColumn("AD_Client_ID", Env.getAD_Client_ID(ctx));
				po.set_ValueOfColumn("AD_Org_ID", Env.getAD_Org_ID(ctx));
				for (String input_C : listCol) {
					X_WS_WebServiceFieldInput inputField = webREST.getFieldInput(input_C);
					MColumn col = MColumn.get(ctx, inputField.getAD_Column_ID());
					if (DisplayType.isDate(col.getAD_Reference_ID()))
						po.set_ValueOfColumn(input_C, Timestamp.valueOf((String)bodyJson.get(input_C)));
					else if (DisplayType.isNumeric (col.getAD_Reference_ID ()))
						po.set_ValueOfColumn(input_C, new BigDecimal((String)bodyJson.get(input_C)));
					else
						po.set_ValueOfColumn(input_C, bodyJson.get(input_C));
				}
				//set value Default_Mandatory
				for (GridField gridField : l_gridFields) {
					if((!gridField.getColumnName().equalsIgnoreCase("AD_Client_ID") && !gridField.getColumnName().equalsIgnoreCase("AD_Org_ID")) 
							&& gridField.isMandatory(true) && !listCol.contains(gridField.getColumnName())) {
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

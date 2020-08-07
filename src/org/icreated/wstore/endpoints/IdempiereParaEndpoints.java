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
package org.icreated.wstore.endpoints;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.icreated.wstore.bean.Order;
import org.icreated.wstore.service.IdempiereParaService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;


@RolesAllowed({"ROLE_USER"})
@Path("/idempierepara")
@Tag(name = "iDempierePara Services")
public class IdempiereParaEndpoints {
	
	    @Context
	    Properties ctx;

	    @GET
		@Path("/web/search/{searchString}")
		@Produces(MediaType.APPLICATION_JSON)
	    @Operation(summary = "Search para WebService", description = "Searching WebServices....")   
		public Response get(
				@Parameter(description = "Searching string", required = true) 
				@PathParam("searchString") String searchString, 
				@Context IdempiereParaService idempiereParaService) {

			//	Search Parameter
			String search = searchString;
		
			return idempiereParaService.doSearchResp(search, null);
		}
		
	    @DELETE
		@Path("/web/search/{searchString}")
		@Produces(MediaType.APPLICATION_JSON)
	    @Operation(summary = "Search para WebService", description = "Searching WebServices....")   
		public Response delete(
				@Parameter(description = "Searching string", required = true) 
				@PathParam("searchString") String searchString,
				@Context IdempiereParaService idempiereParaService) {

			//	Search Parameter
			String search = searchString;
		
			return idempiereParaService.doSearchResp(search, null);
		}
	    
	    @POST
		@Path("/web/search/{searchString}")
		@Produces(MediaType.APPLICATION_JSON)
	    @Consumes(MediaType.APPLICATION_JSON)
	    @Operation(summary = "Search para WebService", description = "Searching WebServices....")   
		public Response post(
				@Parameter(description = "Searching string" ,required = true) 
				@PathParam("searchString") String searchString,
				@RequestBody Map<String, Object> bodyJson,
				@Context IdempiereParaService idempiereParaService) {

			//	Search Parameter
			String search = searchString;
			System.out.println("BODY:::: "+bodyJson.toString());
		
			return idempiereParaService.doSearchResp(search, bodyJson);
		}
	    
	    @PUT
		@Path("/web/search/{searchString}")
		@Produces(MediaType.APPLICATION_JSON)
	    @Consumes(MediaType.APPLICATION_JSON)
	    @Operation(summary = "Search para WebService", description = "Searching WebServices....")   
		public Response put(
				@Parameter(description = "Searching string" ,required = true) 
				@PathParam("searchString") String searchString,
				@RequestBody Map<String, Object> bodyJson,
				@Context IdempiereParaService idempiereParaService) {

			//	Search Parameter
			String search = searchString;
			System.out.println("BODY:::: "+bodyJson.toString());
		
			return idempiereParaService.doSearchResp(search, bodyJson);
		}
}

package com.kubetetris.interaction;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.Response;

@Path("/api/v1/namespaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({MediaType.APPLICATION_JSON})
public class KubernetesResource {

    @POST
    @Path("/{namespace}/pods")
    public Response getPods(@PathParam(value = "namespace") final String namespace) {
        return null;
    }
}

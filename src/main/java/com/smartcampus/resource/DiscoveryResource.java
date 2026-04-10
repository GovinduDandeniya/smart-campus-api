package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApiInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Smart Campus Sensor & Room Management API");
        info.put("version", "1.0");
        info.put("description", "RESTful API for managing campus rooms and sensors");
        info.put("contact", "admin@smartcampus.university.ac.uk");

        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");
        info.put("resources", resources);

        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/v1");
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");
        info.put("_links", links);

        return Response.ok(info).build();
    }
}

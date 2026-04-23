package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.storage.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final Map<String, Sensor> sensors = DataStore.getInstance().getSensors();
    private final Map<String, Room> rooms = DataStore.getInstance().getRooms();

    @Context
    private UriInfo uriInfo;

    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        List<Sensor> sensorList;
        if (type != null && !type.trim().isEmpty()) {
            sensorList = sensors.values().stream()
                    .filter(s -> s.getType().equalsIgnoreCase(type.trim()))
                    .collect(Collectors.toList());
        } else {
            sensorList = new ArrayList<>(sensors.values());
        }
        return Response.ok(sensorList).build();
    }

    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Sensor with ID '" + sensorId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        return Response.ok(sensor).build();
    }

    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor.getId() == null || sensor.getId().trim().isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 400);
            err.put("error", "Bad Request");
            err.put("message", "Sensor ID is required");
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }
        if (sensors.containsKey(sensor.getId())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 409);
            err.put("error", "Conflict");
            err.put("message", "Sensor with ID '" + sensor.getId() + "' already exists");
            return Response.status(Response.Status.CONFLICT).entity(err).build();
        }

        // Validate that the referenced room exists
        if (sensor.getRoomId() == null || !rooms.containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                    "Room with ID '" + sensor.getRoomId() + "' does not exist. "
                    + "Cannot register sensor without a valid room.");
        }

        sensors.put(sensor.getId(), sensor);

        // Link sensor to the room
        Room room = rooms.get(sensor.getRoomId());
        room.getSensorIds().add(sensor.getId());

        URI location = uriInfo.getAbsolutePathBuilder().path(sensor.getId()).build();
        return Response.created(location).entity(sensor).build();
    }

    @PUT
    @Path("/{sensorId}")
    public Response updateSensor(@PathParam("sensorId") String sensorId, Sensor updatedSensor) {
        if (updatedSensor.getId() != null && !updatedSensor.getId().equalsIgnoreCase(sensorId)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 400);
            err.put("error", "Bad Request");
            err.put("message", "Sensor ID in body '" + updatedSensor.getId() + "' does not match path ID '" + sensorId + "'");
            return Response.status(Response.Status.BAD_REQUEST).entity(err).build();
        }
        Sensor existing = sensors.get(sensorId);
        if (existing == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Sensor with ID '" + sensorId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        existing.setType(updatedSensor.getType());
        existing.setStatus(updatedSensor.getStatus());
        // Only update currentValue if explicitly provided (non-zero) in the request
        if (updatedSensor.getCurrentValue() != 0) {
            existing.setCurrentValue(updatedSensor.getCurrentValue());
        }
        return Response.ok(existing).build();
    }

    @DELETE
    @Path("/{sensorId}")
    public Response deleteSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Sensor with ID '" + sensorId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }

        // Remove sensor from room's sensor list
        Room room = rooms.get(sensor.getRoomId());
        if (room != null) {
            room.getSensorIds().remove(sensorId);
        }

        sensors.remove(sensorId);
        return Response.noContent().build();
    }

    @Path("/{sensorId}/readings")
    public SensorReadingResource getSensorReadings(@PathParam("sensorId") String sensorId) {
        Sensor sensor = sensors.get(sensorId);
        if (sensor == null) {
            throw new javax.ws.rs.NotFoundException("Sensor with ID '" + sensorId + "' not found");
        }
        return new SensorReadingResource(sensorId);
    }
}

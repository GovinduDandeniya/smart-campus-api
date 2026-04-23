package com.smartcampus.resource;

import com.smartcampus.model.Room;
import com.smartcampus.exception.RoomNotEmptyException;
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

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final Map<String, Room> rooms = DataStore.getInstance().getRooms();

    @Context
    private UriInfo uriInfo;

    @GET
    public Response getAllRooms() {
        List<Room> roomList = new ArrayList<>(rooms.values());
        return Response.ok(roomList).build();
    }

    @POST
    public Response createRoom(Room room) {
        if (rooms.containsKey(room.getId())) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 409);
            err.put("error", "Conflict");
            err.put("message", "Room with ID '" + room.getId() + "' already exists");
            return Response.status(Response.Status.CONFLICT).entity(err).build();
        }
        rooms.put(room.getId(), room);
        URI location = uriInfo.getAbsolutePathBuilder().path(room.getId()).build();
        return Response.created(location).entity(room).build();
    }

    @GET
    @Path("/{roomId}")
    public Response getRoomById(@PathParam("roomId") String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Room with ID '" + roomId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        return Response.ok(room).build();
    }

    @PUT
    @Path("/{roomId}")
    public Response updateRoom(@PathParam("roomId") String roomId, Room updatedRoom) {
        Room existing = rooms.get(roomId);
        if (existing == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Room with ID '" + roomId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        existing.setName(updatedRoom.getName());
        existing.setCapacity(updatedRoom.getCapacity());
        return Response.ok(existing).build();
    }

    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", 404);
            err.put("error", "Not Found");
            err.put("message", "Room with ID '" + roomId + "' not found");
            return Response.status(Response.Status.NOT_FOUND).entity(err).build();
        }
        if (room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException("Cannot delete room '" + roomId
                    + "': it still has " + room.getSensorIds().size()
                    + " active sensor(s) assigned to it.");
        }
        rooms.remove(roomId);
        return Response.noContent().build();
    }
}

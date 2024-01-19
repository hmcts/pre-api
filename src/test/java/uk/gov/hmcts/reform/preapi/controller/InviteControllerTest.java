package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.InviteController;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.InviteService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
 import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
 import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InviteController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class InviteControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private InviteService inviteService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    private static final String INVITES_ID_PATH = "/invites/{id}";


    @DisplayName("Should get invite by ID with 200 response code")
    @Test
    void testGetInviteByIdSuccess() throws Exception {
        UUID inviteId = UUID.randomUUID();
        InviteDTO mockInviteDTO = new InviteDTO();
        mockInviteDTO.setId(inviteId);
        when(inviteService.findById(inviteId)).thenReturn(mockInviteDTO);

        mockMvc.perform(get(INVITES_ID_PATH, inviteId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(inviteId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing invite")
    @Test
    void testGetNonExistingInviteById() throws Exception {
        UUID inviteId = UUID.randomUUID();
        doThrow(new NotFoundException("Invite: " + inviteId)).when(inviteService).findById(any());

        mockMvc.perform(get(INVITES_ID_PATH, inviteId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Invite: " + inviteId));
    }

    @DisplayName("Should get list of invites with 200 response code")
    @Test
    void testGetInvites() throws Exception {
        String firstName = "Firstname";
        String lastName = "Lastname";
        String email = "example@example.com";
        String organisation = "Organisation";
        InviteDTO mockInviteDTO = new InviteDTO();
        mockInviteDTO.setId(UUID.randomUUID());
        Page<InviteDTO> inviteDTOList = new PageImpl<>(List.of(mockInviteDTO));
        when(inviteService.findAllBy(eq(firstName), eq(lastName), eq(email), eq(organisation), any()))
            .thenReturn(inviteDTOList);

        mockMvc.perform(get("/invites")
                            .param("firstName", firstName)
                            .param("lastName", lastName)
                            .param("email", email)
                            .param("organisation", organisation)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$._embedded.inviteDTOList[0].id").exists());
    }

    // TODO Search params

    @DisplayName("Should create invite with 201 response code")
    @Test
    void testCreateInvite() throws Exception {
        UUID inviteId = UUID.randomUUID();
        var inviteDTO = new CreateInviteDTO();
        inviteDTO.setId(inviteId);
        inviteDTO.setFirstName("Firstname");
        inviteDTO.setLastName("Lastname");
        inviteDTO.setEmail("example@example.com");
        inviteDTO.setOrganisation("Organisation");
        inviteDTO.setPhone("0123456789");
        inviteDTO.setCode("ABCDE");

        when(inviteService.upsert(inviteDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(INVITES_ID_PATH, inviteId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(inviteDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/invites/" + inviteId);
    }

    @DisplayName("Should update invite with 204 response code")
    @Test
    void testUpdateInvite() throws Exception {
        UUID inviteId = UUID.randomUUID();
        var inviteDTO = new CreateInviteDTO();
        inviteDTO.setId(inviteId);

        when(inviteService.upsert(inviteDTO)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(INVITES_ID_PATH, inviteId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(inviteDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/invites/" + inviteId);
    }

    @DisplayName("Should return 400 when creating invite with path and payload mismatch")
    @Test
    void testCreateInvitePathPayloadMismatch() throws Exception {
        UUID inviteId = UUID.randomUUID();
        InviteDTO newInviteRequestDTO = new InviteDTO();
        newInviteRequestDTO.setId(UUID.randomUUID());

        mockMvc.perform(put(INVITES_ID_PATH, inviteId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newInviteRequestDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Path id does not match payload property createInviteDTO.id"));
    }

    @DisplayName("Should delete invite with 200 response code")
    @Test
    void testDeleteInvite() throws Exception {
        UUID inviteId = UUID.randomUUID();
        doNothing().when(inviteService).deleteById(inviteId);

        mockMvc.perform(delete(INVITES_ID_PATH, inviteId)
                            .with(csrf()))
            .andExpect(status().isOk());
    }
}

package com.djt.jukeanator_engine.domain.location.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.djt.jukeanator_engine.AbstractControllerTest;
import com.djt.jukeanator_engine.domain.location.dto.GeoFenceStatusDto;
import com.djt.jukeanator_engine.domain.location.service.GeoFenceService;

class GeoFenceControllerTest extends AbstractControllerTest {

  private static final Integer LOCATION_ID = 7;

  @Mock
  private GeoFenceService geoFenceService;

  @InjectMocks
  private GeoFenceController geoFenceController;

  @Override
  protected Object getController() {
    return geoFenceController;
  }

  @Test
  void getGeoFenceStatus_returnsStatusFromService() throws Exception {
    when(geoFenceService.getStatus(LOCATION_ID))
        .thenReturn(new GeoFenceStatusDto(true, 50.0, 150.0, false, 42.3314, -83.0458));

    mockMvc.perform(get("/api/locations/" + LOCATION_ID + "/geo-fence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enforced", is(true)))
        .andExpect(jsonPath("$.radiusMeters", is(50.0)))
        .andExpect(jsonPath("$.maxAccuracyMeters", is(150.0)))
        .andExpect(jsonPath("$.allowSimulatedPosition", is(false)))
        .andExpect(jsonPath("$.latitude", is(42.3314)))
        .andExpect(jsonPath("$.longitude", is(-83.0458)));
  }
}

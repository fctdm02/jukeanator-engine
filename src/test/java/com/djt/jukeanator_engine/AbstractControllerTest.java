package com.djt.jukeanator_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractControllerTest {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    StandaloneMockMvcBuilder builder = MockMvcBuilders.standaloneSetup(getController());
    configureMockMvc(builder);
    mockMvc = builder.build();
  }

  protected abstract Object getController();

  protected void configureMockMvc(StandaloneMockMvcBuilder builder) {
    // no-op by default; subclasses may override to customize the builder
  }
}

package com.djt.jukeanator_engine.domain.licenseserver.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.AddLicensesRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.CustomerRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.ExtendRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineDto;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.MachineRequest;
import com.djt.jukeanator_engine.domain.licenseserver.dto.LicenseAdminDtos.RevokeRequest;
import com.djt.jukeanator_engine.domain.licenseserver.service.LicenseServerService;

/**
 * License administration (master mode, ADMIN role; see {@code SecurityConfig}). Used by the
 * {@code /admin/licenses.html} page.
 */
@RestController
@RequestMapping("/api/admin/licenses")
@ConditionalOnProperty(name = "app.mode", havingValue = "master")
public class LicenseAdminController {

  private final LicenseServerService licenseServerService;

  public LicenseAdminController(LicenseServerService licenseServerService) {
    this.licenseServerService = licenseServerService;
  }

  @GetMapping("/customers")
  public List<CustomerDto> searchCustomers(@RequestParam(name = "q", required = false) String q) {
    return licenseServerService.searchCustomers(q);
  }

  @GetMapping("/customers/{id}")
  public CustomerDto getCustomer(@PathVariable int id) {
    return licenseServerService.getCustomer(id);
  }

  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerDto createCustomer(@RequestBody CustomerRequest request) {
    return licenseServerService.createCustomer(request);
  }

  @PutMapping("/customers/{id}")
  public CustomerDto updateCustomer(@PathVariable int id, @RequestBody CustomerRequest request) {
    return licenseServerService.updateCustomer(id, request);
  }

  @PostMapping("/customers/{id}/add-licenses")
  public CustomerDto addLicenses(@PathVariable int id, @RequestBody AddLicensesRequest request) {
    return licenseServerService.addLicenses(id, request);
  }

  @PostMapping("/customers/{id}/revoke")
  public CustomerDto revokeCustomer(@PathVariable int id, @RequestBody RevokeRequest request) {
    return licenseServerService.setCustomerRevoked(id, request.revoked());
  }

  @PutMapping("/machines/{id}")
  public MachineDto updateMachine(@PathVariable int id, @RequestBody MachineRequest request) {
    return licenseServerService.updateMachine(id, request);
  }

  @PostMapping("/machines/{id}/extend")
  public MachineDto extendMachine(@PathVariable int id, @RequestBody ExtendRequest request) {
    return licenseServerService.extendMachine(id, request);
  }
}

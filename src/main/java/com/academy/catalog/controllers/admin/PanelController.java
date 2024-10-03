package com.academy.catalog.controllers.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PanelController {

    @GetMapping("/admin/administrationPanel")
    public String administrationPanel(Model model) {
        return "admin/administrationPanel";
    }

}

package com.ronial.app.mail;

import com.ronial.app.models.Email;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MailHtmlFormat {
    public static String toContentHtml(Email email) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=' padding: 20px; background: #f4f6f9; border-radius: 10px; box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.15); max-width: 1000px; margin: auto;'>")
                .append("<h2 style='color: #c62828; font-size: 20px; text-align: center; margin-bottom: 10px;'>📩 Email Details</h2>")
                .append("<p style='color: #666; font-size: 13px; text-align: center;'>🕧 Sent: <strong>").append(email.getCreatedAt()).append("</strong></p>")
                .append("<div style='background: white; padding: 15px; border-radius: 8px; margin-top: 10px;'>")
                .append("<p style='margin: 5px 0; font-size: 14px;'><strong style='color: #00796b;'>👤 From:</strong> ").append(email.getFrom()).append("</p>");

        if (!email.getTransferFrom().isBlank()) {
            html.append("<p style='margin: 5px 0; font-size: 14px; color: #d84315; font-weight: bold;'>🔄 Forwarded from: ")
                    .append(email.getTransferFrom()).append("</p>");
        }

        html.append("<p style='margin: 5px 0; font-size: 14px;'><strong style='color: #00796b;'>👤 To:</strong> ").append(email.getTo()).append("</p>")
                .append("<hr style='border: 0; height: 1px; background: #ddd; margin: 15px 0;'>")
                .append("<p style='font-size: 16px; color: #222; font-weight: bold;'>✉️ Subject: ").append(email.getSubject()).append("</p>")
                .append("<div style='padding: 12px; background: #e3f2fd; border-radius: 6px;'>")
                .append("<p style='font-size: 14px; color: #333; line-height: 1.5;'>").append(email.getBody()).append("</p>")
                .append("</div>")
                .append("</div>");

        if (!email.getLinks().isBlank()) {
            List<String> links = Arrays.stream(email.getLinks().split(";")).toList();
            List<String> linksA = new ArrayList<>();
            if (!links.isEmpty()) {
                html.append("<h3 style='color: #00796b; font-size: 14px; margin-top: 20px; text-align: center;'>🔗 Attached Links:</h3>")
                        .append("<div style='text-align: center; margin-top: 10px;'>");

                // Container for images with Flexbox
                html.append("<div style='display: flex; flex-wrap: wrap; justify-content: center;'>");

                for (String link : links) {
                    if (link.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) {
                        html.append("<img src='").append(link)
                                .append("' width='250' height='250' style='border-radius: 8px; margin: 5px;'>");
                    } else {
                        linksA.add(link);
                    }
                }

                html.append("</div>");

                // Only display text links if linksA is not empty
                if (!linksA.isEmpty()) {
                    for (String link : linksA) {
                        html.append("<a href='").append(link)
                                .append("' style='display: inline-block; background: #1e88e5; color: white; padding: 8px 12px; border-radius: 6px; text-decoration: none; margin: 5px 2px; font-size: 13px;'>")
                                .append("🔗 ").append(link)
                                .append("</a><br/>");
                    }
                }

                html.append("</div>");
            }
        }


        html.append("<hr style='border: 0; height: 1px; background: #ddd; margin: 20px 0;'>")
                .append("<p style='color: gray; font-size: 12px; text-align: right;'>📌 <i>Signature: ").append(email.getFrom()).append("</i></p>")
                .append("</div>");

        return html.toString();
    }
}

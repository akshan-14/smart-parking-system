package com.smartparking.ui.panels;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Statutory Privacy Notice in compliance with India's Digital Personal Data Protection (DPDP) Act, 2023.
 * Supports dual-language display: English (EN) and Hindi (हिंदी).
 *
 * NOTE FOR COLLEGE / PRODUCTION USE:
 * Have this text reviewed before real-world use by a certified legal counsel.
 */
public class PrivacyNoticePanel extends JPanel {

    public PrivacyNoticePanel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(UITheme.BG_CANVAS);
        setBorder(new EmptyBorder(24, 24, 24, 24));

        // Top Banner
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("Statutory Privacy & Data Protection Notice");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(15, 23, 42));

        JLabel sub = new JLabel("Digital Personal Data Protection (DPDP) Act, 2023 — Rights, Purpose, & Redressal");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        titleBox.add(title);
        titleBox.add(sub);
        header.add(titleBox, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);

        // Tabbed Pane for EN and HI
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabs.addTab("English (EN)", buildEnglishView());
        tabs.addTab("हिंदी (Hindi)", buildHindiView());

        add(tabs, BorderLayout.CENTER);
    }

    private JScrollPane buildEnglishView() {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText("<html><body style='font-family:Segoe UI, sans-serif; padding:15px; color:#1e293b; line-height:1.6;'>" +
                "<h2 style='color:#1e3a8a; margin-top:0;'>1. What Personal Data We Collect</h2>" +
                "<ul>" +
                "<li><b>Vehicle Registration Number (License Plate):</b> Captured via Automated Number Plate Recognition (ANPR) camera barriers and manual attendant kiosks.</li>" +
                "<li><b>Mobile Phone Number:</b> Collected during customer registration, digital receipt delivery, wayfinding SMS notifications, and monthly pass issuance.</li>" +
                "<li><b>Timestamps & Telemetry:</b> Entry time, exit time, parking duration, and assigned bay identifier.</li>" +
                "<li><b>Payment Records:</b> Payment method, transaction reference, GST taxable base, and invoice numbers. (Card numbers and CVV are NEVER stored).</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>2. Why We Collect This Data (Specified Purposes)</h2>" +
                "<ul>" +
                "<li>To facilitate barrier gate entry and automated stall allocation.</li>" +
                "<li>To compute parking tariff fees transparently based on dwell time and vehicle class.</li>" +
                "<li>To fulfill statutory tax obligations under the Central Goods and Services Tax (CGST) Act, 2017.</li>" +
                "<li>To assist customers in locating their parked vehicles via the Find Vehicle terminal.</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>3. Statutory Retention Period</h2>" +
                "<ul>" +
                "<li><b>Parking Ticket & Plate Data:</b> Kept active for <b>365 days</b>, after which personal plate details are irreversibly anonymized (e.g. <code>UP00XX0000</code>).</li>" +
                "<li><b>ANPR Camera Snapshots:</b> Purged automatically after <b>30 days</b>.</li>" +
                "<li><b>Tax Invoices & Financial Ledgers:</b> Preserved for statutory tax compliance periods as mandated by Section 36 of the CGST Act.</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>4. Who Can Access Your Data</h2>" +
                "<p>Strict Role-Based Access Control (RBAC) ensures only authorized attendants and system administrators can view operational records. Your personal data is never sold, traded, or shared with external third-party advertisers.</p>" +

                "<h2 style='color:#1e3a8a;'>5. Your Rights as a Data Principal under DPDP Act, 2023</h2>" +
                "<ul>" +
                "<li><b>Right to Access:</b> View all stored personal data through the <b>'My Data'</b> customer portal.</li>" +
                "<li><b>Right to Data Portability:</b> Download your vehicle, ticket, and invoice data in machine-readable JSON or CSV format.</li>" +
                "<li><b>Right to Correction & Erasure:</b> Request erasure of personal identifiers once the primary parking service is completed.</li>" +
                "<li><b>Right of Grievance Redressal:</b> Direct appeals to our designated Grievance Redressal Officer.</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>6. Grievance Officer & Contact Information</h2>" +
                "<p><b>Data Protection & Grievance Officer:</b> Shri R. K. Sharma, Joint Director (Systems)<br>" +
                "<b>Email:</b> <a href='mailto:privacy@smartparking.up.gov.in'>privacy@smartparking.up.gov.in</a> / <a href='mailto:grievance@smartparking.up.gov.in'>grievance@smartparking.up.gov.in</a><br>" +
                "<b>Operational Center:</b> UP Smart Parking Command Center, Knowledge Park III, Greater Noida, Uttar Pradesh 201306</p>" +
                "<p><i>Note: Have this text reviewed by qualified legal counsel before real-world commercial deployment.</i></p>" +
                "</body></html>");
        pane.setCaretPosition(0);
        return new JScrollPane(pane);
    }

    private JScrollPane buildHindiView() {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText("<html><body style='font-family:Segoe UI, sans-serif; padding:15px; color:#1e293b; line-height:1.6;'>" +
                "<h2 style='color:#1e3a8a; margin-top:0;'>१. हम क्या व्यक्तिगत डेटा एकत्र करते हैं</h2>" +
                "<ul>" +
                "<li><b>वाहन पंजीकरण संख्या (नंबर प्लेट):</b> स्वचालित एएनपीआर (ANPR) कैमरों एवं गेट कियोस्क द्वारा दर्ज।</li>" +
                "<li><b>मोबाइल फोन नंबर:</b> डिजिटल रसीद, वे-फाइंडिंग एसएमएस तथा मासिक पास प्रबंधन हेतु।</li>" +
                "<li><b>प्रवेश एवं निकास समय:</b> वाहन का प्रवेश समय, निकास समय एवं आवंटित पार्किंग स्लॉट।</li>" +
                "<li><b>भुगतान रिकॉर्ड:</b> भुगतान विधि, लेनदेन संदर्भ एवं जीएसटी इनवॉइस विवरण (सीवीवी या पूर्ण कार्ड नंबर कभी सहेजे नहीं जाते)।</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>२. डेटा संग्रह का उद्देश्य</h2>" +
                "<ul>" +
                "<li>गेट बैरियर नियंत्रण एवं पार्किंग स्लॉट का स्वचालित आवंटन।</li>" +
                "<li>पार्किंग शुल्क की पारदर्शी गणना एवं जीएसटी कर अनुपालन।</li>" +
                "<li>'फाइंड माय व्हीकल' सुविधा द्वारा वाहन खोजने में सहायता।</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>३. डेटा भंडारण एवं प्रतिधारण अवधि (Data Retention)</h2>" +
                "<ul>" +
                "<li><b>टिकट एवं वाहन डेटा:</b> ३६५ दिनों के उपरांत वाहन प्लेट को स्थायी रूप से गुमनाम (उदा. <code>UP00XX0000</code>) कर दिया जाता है।</li>" +
                "<li><b>कैमरा तस्वीरें:</b> ३० दिनों के पश्चात स्वचालित रूप से नष्ट कर दी जाती हैं।</li>" +
                "<li><b>जीएसटी टैक्स इनवॉइस:</b> वैधानिक कर नियमों के अंतर्गत वित्तीय लेखापरीक्षा हेतु सुरक्षित रखे जाते हैं।</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>४. डीपीडीपी अधिनियम, २०२३ के तहत आपके अधिकार</h2>" +
                "<ul>" +
                "<li><b>डेटा देखने का अधिकार:</b> 'माई डेटा' (My Data) स्क्रीन से अपनी सभी प्रविष्टियां देखें।</li>" +
                "<li><b>डेटा डाउनलोड:</b> अपने रिकॉर्ड्स को JSON या CSV प्रारूप में डाउनलोड करें।</li>" +
                "<li><b>डेटा विलोपन (Right to Erasure):</b> अपनी व्यक्तिगत पहचान को हटाने हेतु अनुरोध दर्ज करें।</li>" +
                "</ul>" +

                "<h2 style='color:#1e3a8a;'>५. शिकायत निवारण अधिकारी (Grievance Officer)</h2>" +
                "<p><b>अधिकारी:</b> श्री आर. के. शर्मा, संयुक्त निदेशक (प्रणाली)<br>" +
                "<b>ईमेल:</b> privacy@smartparking.up.gov.in / grievance@smartparking.up.gov.in<br>" +
                "<b>कार्यालय:</b> उत्तर प्रदेश स्मार्ट पार्किंग नियंत्रण केंद्र, नॉलेज पार्क, ग्रेटर नोएडा (उ.प्र.)</p>" +
                "</body></html>");
        pane.setCaretPosition(0);
        return new JScrollPane(pane);
    }
}

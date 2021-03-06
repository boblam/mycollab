/**
 * This file is part of mycollab-web.
 *
 * mycollab-web is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-web is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-web.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.shell.view;

import com.esofthead.mycollab.common.i18n.GenericI18Enum;
import com.esofthead.mycollab.common.i18n.ShellI18nEnum;
import com.esofthead.mycollab.configuration.ApplicationProperties;
import com.esofthead.mycollab.configuration.EmailConfiguration;
import com.esofthead.mycollab.configuration.SiteConfiguration;
import com.esofthead.mycollab.core.UserInvalidInputException;
import com.esofthead.mycollab.servlet.InstallUtils;
import com.esofthead.mycollab.vaadin.AppContext;
import com.esofthead.mycollab.vaadin.ui.*;
import com.vaadin.event.ShortcutAction;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.*;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vaadin.dialogs.ConfirmDialog;
import org.vaadin.maddon.layouts.MHorizontalLayout;
import org.vaadin.maddon.layouts.MVerticalLayout;

import java.io.File;

/**
 * @author MyCollab Ltd.
 * @since 5.0.4
 */
public class SmtpConfigurationWindow extends Window {
    private static Logger LOG = LoggerFactory.getLogger(SmtpConfigurationWindow.class);
    private EmailConfiguration emailConfiguration;
    private AdvancedEditBeanForm<EmailConfiguration> editForm;

    public SmtpConfigurationWindow() {
        super("SMTP Setting");
        this.center();
        this.setResizable(false);
        this.setModal(true);
        this.setWidth("600px");

        MVerticalLayout contentLayout = new MVerticalLayout().withMargin(false);
        this.setContent(contentLayout);

        emailConfiguration = new EmailConfiguration();
        editForm = new AdvancedEditBeanForm<>();
        contentLayout.addComponent(editForm);
        setCaption("Set up the new SMTP account");
        editForm.setFormLayoutFactory(new FormLayoutFactory());
        editForm.setBeanFormFieldFactory(new EditFormFieldFactory(editForm));
        editForm.setBean(emailConfiguration);
    }

    private class EditFormFieldFactory extends
            AbstractBeanFieldGroupEditFieldFactory<EmailConfiguration> {
        private static final long serialVersionUID = 1L;

        public EditFormFieldFactory(GenericBeanForm<EmailConfiguration> form) {
            super(form);
        }

        @Override
        protected Field<?> onCreateField(final Object propertyId) {
            if (propertyId.equals("isTls")) {
                return new CheckBox("", false);
            }
            return null;
        }
    }

    class FormLayoutFactory implements IFormLayoutFactory {
        private static final long serialVersionUID = 1L;

        private GridFormLayoutHelper informationLayout;

        @Override
        public ComponentContainer getLayout() {
            final VerticalLayout projectAddLayout = new VerticalLayout();

            this.informationLayout = GridFormLayoutHelper.defaultFormLayoutHelper(2, 5);
            projectAddLayout.addComponent(this.informationLayout.getLayout());

            final MHorizontalLayout buttonControls = new MHorizontalLayout().withMargin(true).withStyleName("addNewControl");

            final Button closeBtn = new Button(
                    AppContext.getMessage(GenericI18Enum.BUTTON_CLOSE),
                    new Button.ClickListener() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void buttonClick(final Button.ClickEvent event) {
                            SmtpConfigurationWindow.this.close();
                        }

                    });
            closeBtn.setStyleName(UIConstants.THEME_GRAY_LINK);
            buttonControls.with(closeBtn).withAlign(closeBtn, Alignment.MIDDLE_RIGHT);

            final Button saveBtn = new Button(
                    AppContext.getMessage(GenericI18Enum.BUTTON_SAVE),
                    new Button.ClickListener() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void buttonClick(final Button.ClickEvent event) {
                            if (editForm.validateForm()) {
                                String isTLS = (emailConfiguration.getIsTls()) ? "TLS" : "";
                                boolean isSetupValid = InstallUtils.checkSMTPConfig(emailConfiguration.getHost(), emailConfiguration.getPort(), emailConfiguration.getUser(), emailConfiguration.getPassword(), true, isTLS);
                                if (!isSetupValid) {
                                    ConfirmDialogExt.show(
                                            UI.getCurrent(),
                                            "Invalid SMTP account?",
                                            "We can not connect to the SMTP server. Save the configuration anyway?",
                                            AppContext
                                                    .getMessage(GenericI18Enum.BUTTON_YES),
                                            AppContext
                                                    .getMessage(GenericI18Enum.BUTTON_NO),
                                            new ConfirmDialog.Listener() {
                                                private static final long serialVersionUID = 1L;

                                                @Override
                                                public void onClose(ConfirmDialog dialog) {
                                                    if (dialog.isConfirmed()) {
                                                        saveEmailConfiguration();
                                                    }
                                                }
                                            });
                                } else {
                                    saveEmailConfiguration();
                                }
                                SmtpConfigurationWindow.this.close();
                            }
                        }
                    });
            saveBtn.setStyleName(UIConstants.THEME_GREEN_LINK);
            saveBtn.setIcon(FontAwesome.SAVE);
            saveBtn.setClickShortcut(ShortcutAction.KeyCode.ENTER);
            buttonControls.with(saveBtn).withAlign(saveBtn, Alignment.MIDDLE_RIGHT);

            projectAddLayout.addComponent(buttonControls);
            projectAddLayout.setComponentAlignment(buttonControls,
                    Alignment.MIDDLE_RIGHT);
            return projectAddLayout;
        }

        private void saveEmailConfiguration() {
            SiteConfiguration.setEmailConfiguration(emailConfiguration);
            File configFile = ApplicationProperties.getAppConfigFile();
            if (configFile != null) {
                try {
                    PropertiesConfiguration p = new PropertiesConfiguration(ApplicationProperties.getAppConfigFile());
                    p.setProperty(ApplicationProperties.MAIL_SMTPHOST, emailConfiguration.getHost());
                    p.setProperty(ApplicationProperties.MAIL_USERNAME, emailConfiguration.getUser());
                    p.setProperty(ApplicationProperties.MAIL_PASSWORD, emailConfiguration.getPassword());
                    p.setProperty(ApplicationProperties.MAIL_PORT, emailConfiguration.getPort());
                    p.setProperty(ApplicationProperties.MAIL_IS_TLS, emailConfiguration.getIsTls());
                    p.save();
                    NotificationUtil.showNotification("Set up SMTP account successfully");
                } catch (Exception e) {
                    LOG.error("Can not save email props", e);
                    throw new UserInvalidInputException("Can not save properties file successfully");
                }
            }
        }

        @Override
        public void attachField(final Object propertyId, final Field<?> field) {
            if (propertyId.equals("host")) {
                this.informationLayout.addComponent(field, "Host", 0, 0);
            } else if (propertyId.equals("user")) {
                this.informationLayout.addComponent(field, "User Name", 0, 1);
            } else if (propertyId.equals("password")) {
                this.informationLayout.addComponent(field, "Password", 0, 2);
            } else if (propertyId.equals("port")) {
                this.informationLayout.addComponent(field, "Port", 0, 3);
            } else if (propertyId.equals("isTls")) {
                this.informationLayout.addComponent(field, "SSL/TLS", 0, 4);
            }
        }
    }
}

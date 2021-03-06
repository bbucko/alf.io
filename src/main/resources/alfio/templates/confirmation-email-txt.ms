{{#i18n}}email.hello [{{ticketReservation.fullName}}]{{/i18n}}

{{#i18n}}email-confirmation.completed [{{event.shortName}}] [{{reservationUrl}}]{{/i18n}} 

#### {{#i18n}}email-confirmation.reservation-summary{{/i18n}} ####

{{#orderSummary.summary}}
{{#i18n}}email-confirmation.summary [{{name}}] [{{amount}}] [{{subTotal}}] [{{event.currency}}]{{/i18n}}{{/orderSummary.summary}}
{{^orderSummary.free}}{{^event.vatIncluded}}
{{#i18n}}reservation-page.vat [{{event.vat}}]{{/i18n}}: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}
{{#i18n}}reservation-page.total{{/i18n}} {{orderSummary.totalPrice}} {{event.currency}}
{{^orderSummary.free}}{{#event.vatIncluded}}
{{#i18n}}reservation-page.vat-included [{{event.vat}}]{{/i18n}}: {{orderSummary.totalVAT}} {{event.currency}}{{/event.vatIncluded}}{{/orderSummary.free}}

{{#hasVat}}{{#i18n}}email-confirmation.vatNr [{{vatNr}}]{{/i18n}}{{/hasVat}}

{{#i18n}}email-confirmation.order-information [{{ticketReservation.id}}]{{/i18n}}

#### {{#i18n}}email.event-info{{/i18n}} ####

{{#i18n}}email.event-reminder-location{{/i18n}}

 {{event.location}} 

{{#i18n}}email.event-reminder-date{{/i18n}}

{{#event.sameDay}}{{#i18n}}event-days.same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}] {{/i18n}}{{/event.sameDay}}
{{^event.sameDay}}{{#i18n}}event-days.not-same-day [{{#format-date}}{{event.begin}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.begin}} HH:mm{{/format-date}}]{{/i18n}} - {{#i18n}}event-days.not-same-day [{{#format-date}}{{event.end}} EEEE dd MMMM yyyy locale:{{#i18n}}locale{{/i18n}}{{/format-date}}] [{{#format-date}}{{event.end}} HH:mm (z){{/format-date}}]{{/i18n}} {{/event.sameDay}}

{{#i18n}}email.kind-regards{{/i18n}}

{{organization.name}} <{{organization.email}}>
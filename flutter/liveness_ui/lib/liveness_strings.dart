/// Static UI chrome (titles, buttons). Per-state and per-challenge messages come localized from the engine.
class LivenessStrings {
  final String locale;
  const LivenessStrings(this.locale);

  bool get rtl => locale == 'ar';

  static const _t = <String, Map<String, String>>{
    'en': {
      'title.resident': 'Resident face capture',
      'title.operator': 'Operator authentication',
      'title.supervisor': 'Supervisor authentication',
      'btn.start': 'Start',
      'btn.cancel': 'Cancel',
      'btn.retry': 'Try again',
      'btn.close': 'Close',
      'a11y.oval': 'Face positioning oval',
    },
    'fr': {
      'title.resident': 'Capture du visage du resident',
      'title.operator': "Authentification de l'operateur",
      'title.supervisor': 'Authentification du superviseur',
      'btn.start': 'Demarrer',
      'btn.cancel': 'Annuler',
      'btn.retry': 'Reessayer',
      'btn.close': 'Fermer',
      'a11y.oval': 'Ovale de positionnement du visage',
    },
    'ar': {
      'title.resident': '\u0627\u0644\u062A\u0642\u0627\u0637 \u0648\u062C\u0647 \u0627\u0644\u0645\u0642\u064A\u0645',
      'title.operator': '\u0645\u0635\u0627\u062F\u0642\u0629 \u0627\u0644\u0645\u0634\u063A\u0644',
      'title.supervisor': '\u0645\u0635\u0627\u062F\u0642\u0629 \u0627\u0644\u0645\u0634\u0631\u0641',
      'btn.start': '\u0627\u0628\u062F\u0623',
      'btn.cancel': '\u0625\u0644\u063A\u0627\u0621',
      'btn.retry': '\u062D\u0627\u0648\u0644 \u0645\u0631\u0629 \u0623\u062E\u0631\u0649',
      'btn.close': '\u0625\u063A\u0644\u0627\u0642',
      'a11y.oval': '\u0628\u064A\u0636\u0627\u0648\u064A \u062A\u0645\u0648\u0636\u0639 \u0627\u0644\u0648\u062C\u0647',
    },
  };

  String of(String key) => (_t[locale] ?? _t['en']!)[key] ?? _t['en']![key] ?? key;
}

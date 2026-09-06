"""Validate documentation fixtures, schema failures, references and design contrast.

Run from repository root: python tools/validate_docs.py
This is a documentation conformance check, not the Android runtime validator.
"""
import copy
import json
import re
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / 'docs/schemas'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


schemas = [read(p) for p in sorted(SCHEMAS.glob('*.json'))]
registry = Registry().with_resources((s['$id'], Resource.from_contents(s)) for s in schemas)
validators = {}
assert not FormatChecker().conforms('2026-09-08T19:00:00', 'date-time'), 'Install requirements-docs.txt: RFC3339 checker is required'
for schema in schemas:
    Draft202012Validator.check_schema(schema)
    validators[schema['properties']['schema']['const']] = Draft202012Validator(
        schema, registry=registry, format_checker=FormatChecker())


def unique(items):
    assert len(items) == len(set(items)), 'duplicate identity'


def check_program(program, catalog):
    unique([e['equipment_id'] for e in program['equipment_upserts']])
    known = set(catalog) | {e['equipment_id'] for e in program['equipment_upserts']}
    for eq in program['equipment_upserts']:
        weights = eq.get('available_weights_kg')
        if weights is not None:
            assert weights == sorted(set(weights)), 'weights must increase'
    unique([w['workout_instance_id'] for w in program['workouts']])
    for workout in program['workouts']:
        unique([b['block_id'] for b in workout['blocks']])
        exercises = [e for b in workout['blocks'] for e in b['exercises']]
        unique([e['exercise_instance_id'] for e in exercises])
        for block in workout['blocks']:
            unique([e['planned_order'] for e in block['exercises']])
        for exercise in exercises:
            assert exercise['equipment_id'] is None or exercise['equipment_id'] in known
            assert [s['set_no'] for s in exercise['planned_sets']] == list(range(1, len(exercise['planned_sets']) + 1))
            for planned in exercise['planned_sets']:
                assert planned['reps_min'] <= planned['reps_max'], 'invalid rep range'


def validate(data):
    validators[data['schema']].validate(data)
    if data['schema'] == 'sportzal.program':
        check_program(data, set())
        return
    catalog = {e['equipment_id'] for e in data['equipment']}
    assert len(catalog) == len(data['equipment'])
    for eq in data['equipment']:
        if eq.get('available_weights_kg') is not None:
            assert eq['available_weights_kg'] == sorted(set(eq['available_weights_kg']))
    versions = {(p['program_id'], p['program_version']): p for p in data['programs']}
    assert len(versions) == len(data['programs'])
    for program in data['programs']:
        check_program(program, catalog)
    active = data['active_program']
    assert active is None or (active['program_id'], active['program_version']) in versions
    unique([w['workout_id'] for w in data['workouts']])
    assert data['focus_workout_id'] is None or data['focus_workout_id'] in {w['workout_id'] for w in data['workouts']}
    assert sum(w['completion_status'] == 'active' for w in data['workouts']) <= 1
    unique([s['set_result_id'] for w in data['workouts'] for s in w['sets']])
    scope = data['history_scope']
    assert scope['included_workouts'] == len(data['workouts'])
    assert scope['total_stored_workouts'] == scope['included_workouts'] + scope['omitted_workouts']
    for workout in data['workouts']:
        program = versions[(workout['program_id'], workout['program_version'])]
        plan = next(w for w in program['workouts'] if w['workout_instance_id'] == workout['workout_instance_id'])
        assert plan['template_id'] == workout['template_id']
        instances = {e['exercise_instance_id'] for b in plan['blocks'] for e in b['exercises']}
        start_equipment = {e['equipment_id'] for e in workout['equipment_at_start']}
        assert len(start_equipment) == len(workout['equipment_at_start'])
        assert all(e['equipment_id'] is None or e['equipment_id'] in start_equipment for b in plan['blocks'] for e in b['exercises'])
        slots = {(e['exercise_instance_id'], s['set_no']): s for b in plan['blocks'] for e in b['exercises'] for s in e['planned_sets']}
        filled, skipped = set(), set()
        unique([s['sequence_no'] for s in workout['sets']])
        for actual in workout['sets']:
            assert actual['exercise_instance_id'] in instances
            assert actual['equipment_id_actual'] is None or actual['equipment_id_actual'] in catalog
            if actual['planned_set_no'] is not None:
                key = (actual['exercise_instance_id'], actual['planned_set_no'])
                assert key in slots and key not in filled
                assert actual['set_type'] == slots[key]['set_type']
                filled.add(key)
        for skip in workout['skipped_sets']:
            key = (skip['exercise_instance_id'], skip['planned_set_no'])
            assert key in slots and key not in filled and key not in skipped
            skipped.add(key)
        if workout['completion_status'] != 'active':
            assert (workout['completion_status'] == 'completed') == (filled == set(slots))


program = read(ROOT / 'docs/examples/program.json')
snapshot = read(ROOT / 'docs/examples/ai-snapshot.json')
assert snapshot['programs'][0] == program, 'example program drift'
validate(program)
validate(snapshot)
positive = 2

# Legitimate edge states: initial export, active session, extra set, clock rollback.
empty = copy.deepcopy(snapshot)
empty.update(workouts=[], focus_workout_id=None,
             history_scope=dict(completed_limit=24, total_stored_workouts=0, included_workouts=0, omitted_workouts=0))
validate(empty)
active = copy.deepcopy(snapshot)
active['workouts'][0].update(completion_status='active', finished_at=None)
validate(active)
extra = copy.deepcopy(snapshot)
record = copy.deepcopy(extra['workouts'][0]['sets'][0])
record.update(set_result_id='extra', sequence_no=7, planned_set_no=None)
extra['workouts'][0]['sets'].append(record)
validate(extra)
rollback = copy.deepcopy(snapshot)
rollback['workouts'][0]['sets'][-1]['completed_at'] = '2026-09-08T18:00:00+03:00'
validate(rollback)
positive += 4

negative = 0


def rejects(name, original, mutate):
    global negative
    data = copy.deepcopy(original)
    mutate(data)
    try:
        validate(data)
    except (AssertionError, ValueError, KeyError, StopIteration) as error:
        negative += 1
    except Exception as error:
        # JSON Schema errors are intentionally separate from fixture assertions.
        from jsonschema.exceptions import ValidationError
        if not isinstance(error, ValidationError):
            raise
        negative += 1
    else:
        raise AssertionError('invalid case accepted: ' + name)


def first_ex(d): return d['workouts'][0]['blocks'][0]['exercises'][0]
def first_set(d): return d['workouts'][0]['sets'][0]


rejects('unsupported version', program, lambda d: d.update(schema_version=2))
rejects('one-exercise rotation', program, lambda d: d['workouts'][0]['blocks'][0].update(exercises=[first_ex(d)]))
rejects('multiple straight', program, lambda d: d['workouts'][0]['blocks'][0].update(mode='straight'))
rejects('negative weight', program, lambda d: first_ex(d)['planned_sets'][0].update(target_weight_kg=-1))
rejects('inverted range', program, lambda d: first_ex(d)['planned_sets'][0].update(reps_min=12, reps_max=8))
rejects('unknown equipment', program, lambda d: first_ex(d).update(equipment_id='missing'))
rejects('duplicate instance', program, lambda d: d['workouts'][0]['blocks'][1]['exercises'][0].update(exercise_instance_id=first_ex(d)['exercise_instance_id']))
rejects('unordered slots', program, lambda d: first_ex(d)['planned_sets'][1].update(set_no=5))
rejects('invalid date', program, lambda d: d['workouts'][0].update(planned_date='2026-02-30'))
rejects('unordered weights', program, lambda d: d['equipment_upserts'][0].update(available_weights_kg=[40,20]))
rejects('bodyweight with load', program, lambda d: first_ex(d).update(load_basis='bodyweight'))
rejects('unknown field', snapshot, lambda d: d.update(progress_score=100))
rejects('fractional reps', snapshot, lambda d: first_set(d).update(reps=1.5))
rejects('RIR outside buckets', snapshot, lambda d: first_set(d).update(rir=5))
rejects('timestamp without offset', snapshot, lambda d: first_set(d).update(completed_at='2026-09-08T19:00:00'))
rejects('missing version reference', snapshot, lambda d: d['workouts'][0].update(program_version=99))
rejects('false completed status', snapshot, lambda d: d['workouts'][0].update(completion_status='completed', skipped_sets=[]))
rejects('finished active', snapshot, lambda d: d['workouts'][0].update(completion_status='active'))
rejects('duplicate sequence', snapshot, lambda d: d['workouts'][0]['sets'][1].update(sequence_no=1))
rejects('duplicate slot', snapshot, lambda d: d['workouts'][0]['sets'][3].update(planned_set_no=1, set_type='warmup'))
rejects('skip overlaps fact', snapshot, lambda d: d['workouts'][0]['skipped_sets'][0].update(planned_set_no=2))
rejects('false history scope', snapshot, lambda d: d['history_scope'].update(included_workouts=24))
rejects('missing focused workout', snapshot, lambda d: d.update(focus_workout_id='missing'))
rejects('lost actual equipment', snapshot, lambda d: first_set(d).update(equipment_id_actual='missing'))
rejects('lost historical planned equipment', snapshot, lambda d: d['workouts'][0].update(equipment_at_start=[]))

# Check local Markdown links and normative paths embedded in code spans.
for path in ROOT.rglob('*.md'):
    content = path.read_text(encoding='utf-8')
    for target in re.findall(r'\]\(([^)]+)\)', content):
        if not re.match(r'\w+://|#', target):
            assert (path.parent / target.split('#')[0]).exists(), (path, target)
    for target in re.findall(r'`(docs/[^`]+\.(?:md|json))`', content):
        assert (ROOT / target).exists(), (path, target)

design = yaml.safe_load((ROOT / 'DESIGN.md').read_text().split('---')[1])


def luminance(hex_value):
    rgb = [int(hex_value[i:i+2], 16) / 255 for i in (1,3,5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(x * w for x, w in zip(linear, (.2126, .7152, .0722)))


for foreground in ('ink', 'ink-secondary', 'timer'):
    for background in ('canvas', 'surface', 'surface-muted'):
        a, b = sorted([luminance(design['colors'][foreground]), luminance(design['colors'][background])])
        assert (b + .05) / (a + .05) >= 4.5, (foreground, background)
print(f'PASS: {len(schemas)} schemas; {positive} valid cases; {negative} rejected cases; links; YAML; 9 text contrast pairs.')

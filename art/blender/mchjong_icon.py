"""Blender 5.2: rounded mahjong tile with coplanar grass-block artwork."""
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.preferences.filepaths.save_version = 0


def material(name, color, roughness=0.35):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color, 1)
    shader = mat.node_tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = roughness
    shader.inputs['Specular IOR Level'].default_value = 0.18
    return mat


def body(name, size, z, bevel, mat):
    bpy.ops.mesh.primitive_cube_add(location=(0, 0, z))
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(mat)
    mod = obj.modifiers.new('Rounded resin edges', 'BEVEL')
    mod.width, mod.segments = bevel, 12
    for face in obj.data.polygons:
        face.use_smooth = True
    obj.modifiers.new('Flat face normals', 'WEIGHTED_NORMAL')


def aim(obj, point):
    obj.rotation_euler = (Vector(point) - obj.location).to_track_quat('-Z', 'Y').to_euler()


ivory = material('Warm ivory', (0.89, 0.865, 0.72), 0.24)
jade = material('Deep green resin', (0.004, 0.065, 0.027), 0.28)
body('Mahjong green back', (2.9, 4.3, 1.18), 0.63, 0.24, jade)
body('Mahjong ivory face', (2.9, 4.3, 0.46), 1.26, 0.21, ivory)

# Three illustrated vertical edges are exactly parallel to local Y.
top, left, right, middle = (0, 1.15), (-0.98, 0.61), (0.98, 0.61), (0, 0.07)
lower_left, lower_right, bottom = (-0.98, -0.57), (0.98, -0.57), (0, -1.11)


def drop(point):
    return (point[0], point[1] - 0.22)


polygons = [
    [top, left, middle, right],
    [left, drop(left), drop(middle), middle],
    [middle, drop(middle), drop(right), right],
    [drop(left), lower_left, bottom, drop(middle)],
    [drop(middle), bottom, lower_right, drop(right)],
]
colors = [(0.075, 0.22, 0.025), (0.045, 0.135, 0.012), (0.025, 0.095, 0.007),
          (0.17, 0.067, 0.024), (0.095, 0.032, 0.010)]
vertices, faces = [], []
# Center the full printed outline on the ivory face, in its local XY plane.
outline = [point for polygon in polygons for point in polygon]
center_x = (min(p[0] for p in outline) + max(p[0] for p in outline)) / 2
center_y = (min(p[1] for p in outline) + max(p[1] for p in outline)) / 2
for polygon in polygons:
    start = len(vertices)
    vertices.extend((x - center_x, y - center_y, 1.4902) for x, y in polygon)
    faces.append(tuple(range(start, start + len(polygon))))
mesh = bpy.data.meshes.new('Coplanar printed artwork')
mesh.from_pydata(vertices, [], faces)
mesh.update()
emblem = bpy.data.objects.new('Flat grass block print', mesh)
bpy.context.collection.objects.link(emblem)
for index, color in enumerate(colors):
    mesh.materials.append(material('Printed ink ' + str(index), color, 0.48))
    mesh.polygons[index].material_index = index
for upper, lower in [(left, lower_left), (middle, bottom), (right, lower_right)]:
    assert upper[0] == lower[0]
assert len({v.co.z for v in mesh.vertices}) == 1
emblem['alignment'] = 'All three upright edges parallel to tile local Y'

for name, position, power, size in [
    ('Large softbox', (-4, -3, 8), 650, 5),
    ('Right fill', (4, 1, 6), 280, 4),
    ('Edge highlight', (-1, 5, 5), 350, 3),
]:
    data = bpy.data.lights.new(name, 'AREA')
    data.energy, data.shape, data.size = power, 'DISK', size
    obj = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(obj)
    obj.location = position
    aim(obj, (0, 0, 0.4))
scene = bpy.context.scene
world = bpy.data.worlds.new('White studio')
scene.world = world
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (1, 1, 1, 1)
world.node_tree.nodes['Background'].inputs[1].default_value = 0.45
data = bpy.data.cameras.new('Orthographic icon camera')
camera = bpy.data.objects.new('Orthographic icon camera', data)
bpy.context.collection.objects.link(camera)
camera.location = (5, -8, 17)
aim(camera, (0, 0, 0.75))
camera.rotation_euler.rotate_axis('Z', -0.23)
data.type, data.ortho_scale = 'ORTHO', 5.85
scene.camera = camera
scene.render.engine = 'CYCLES'
scene.cycles.samples = 64
scene.cycles.use_denoising = True
scene.render.resolution_x = scene.render.resolution_y = 1024
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.film_transparent = True
scene.render.filepath = str(ROOT / 'common/src/main/resources/assets/mchjong/icon.png')
scene.view_settings.view_transform = 'AgX'
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / 'art/blender/mchjong_icon.blend'))
bpy.ops.render.render(write_still=True)
